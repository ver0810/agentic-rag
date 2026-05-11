# RAG 链路优化方案

> 基于当前实现的完整分析，按优先级分阶段规划。

---

## 当前链路总览

```
用户查询 → [查询改写] → 向量化 → 混合检索(向量+关键词) → [词法重排] → 上下文组装 → LLM生成 → 返回
```

**已实现：** 混合检索、LLM 改写、词法重排、全链路追踪框架、异步文档处理、分阶段耗时审计

---

## 第一阶段：检索质量提升

### 1.1 混合检索融合算法升级 [P0]

**现状：** 加权求和融合（vector×0.75 + keyword×0.25），要求两路分数分布可比，实际不可比。

**方案：** 改用 RRF（Reciprocal Rank Fusion）

```
RRF_score(d) = Σ 1/(k + rank_i(d))    k 通常取 60
```

- 不依赖原始分数，只看排名
- 天然适配不同分数分布的融合
- 实现简单，改动仅在 `DefaultRagQueryService.mergeResults()`

**涉及文件：**
- `rag/query/DefaultRagQueryService.java`

**验收标准：** 使用 rageval 模块对比改前改后的 passRate

---

### 1.2 引入 Cross-Encoder Reranker [P0]

**现状：** `RagRerankService` 只做词频重叠（大粒度中文二元组 + 字符串 contains），非语义级重排。

**方案：** 接入真正的 Cross-Encoder Reranker

| 方案 | 说明 |
|---|---|
| 本地模型 | 集成 `bge-reranker-v2-m3`，通过 ONNX Runtime 推理 |
| API 服务 | 调用 Cohere Rerank API 或自部署 Jina Reranker |
| 端口抽象 | 在 `infra-ai` 定义 `RerankerPort`，bootstrap 提供适配器 |

**改造点：**
- `RagRerankService` 改为策略模式，支持 `lexical` / `cross-encoder` 切换
- `RagProperties` 新增 `reranker-type` 配置项
- blend 权重（当前硬编码 0.8/0.2）改为可配置

**涉及文件：**
- `rag/query/RagRerankService.java`
- `infra-ai/.../config/RagProperties.java`
- 新增 `infra-ai/.../port/reranker/RerankerPort.java`
- 新增 `infrastructure/reranker/` 适配器

**验收标准：** rageval 对比 rerank 前后的 citationHitRate

---

### 1.3 查询改写增强 [P1]

**现状：** 单次改写，无分解能力，200 字符硬截断，使用通用 chat 场景（成本高）。

**优化方向：**

| 技术 | 说明 |
|---|---|
| Multi-Query | 生成 2-3 个不同视角的改写，分别检索后合并去重 |
| Query Decomposition | 将复合问题拆分为子问题，逐个检索再汇总 |
| HyDE | 先让 LLM 生成假设性回答，用回答的 embedding 做检索 |
| Step-Back Prompting | 先生成更抽象的问题，再结合原始问题检索 |

**改造点：**
- `RagQueryRewriteService.rewrite()` 返回 `List<String>` 而非单个 `String`
- `DefaultRagQueryService` 支持多 query 分别检索后合并
- 改写 prompt 外部化，可配置
- 为改写场景使用轻量模型降低成本

**涉及文件：**
- `rag/query/RagQueryRewriteService.java`
- `rag/query/DefaultRagQueryService.java`

**验收标准：** rageval 对比 rewrite 前后的 passRate

---

### 1.4 相似度阈值与 topK 粒度优化 [P1]

**现状：** `similarityThreshold` 是全局配置，不能按知识库调整。

**方案：**
- `similarityThreshold` 支持按知识库配置（存入 `t_knowledge_base` 表）
- 检索时优先使用知识库级别阈值，fallback 到全局配置
- `vectorTopK` / `keywordTopK` 同理

**涉及文件：**
- `knowledge/dao/entity/KnowledgeBaseEntity.java`
- `rag/query/DefaultRagQueryService.java`
- `infra-ai/.../config/RagProperties.java`

---

## 第二阶段：文档处理质量提升

### 2.1 分块策略优化 [P1]

**现状：** 仅 `fixed`（固定字符 500）和 `paragraph`（段落感知 900）两种策略。无语义分块，无 token 感知。

**优化方向：**

| 策略 | 适用场景 | 实现复杂度 |
|---|---|---|
| 递归字符分割 | 通用场景，LangChain 经典方案 | 低 |
| 语义分块 | 长文档、主题频繁切换 | 中 |
| Token 感知分块 | 避免超出 embedding 模型上限 | 低 |
| Late Chunking | 利用长上下文模型保留全局信息 | 高 |

**改造点：**
- `DocumentChunkingService` 新增策略实现
- `chunkStrategy` 字段支持新策略名
- `chunkConfig` 支持对应参数

**涉及文件：**
- `knowledge/service/DocumentChunkingService.java`
- `knowledge/dao/entity/KnowledgeDocumentEntity.java`

---

### 2.2 Token 感知分块 [P1]

**现状：** 分块按字符数，但 embedding 模型有 token 上限（如 text-embedding-3-small 为 8192）。中文 1 token ≈ 1-2 字符，500 字符的 chunk 可能接近 500 token。

**方案：**
- 分块时同时检查字符数和 token 数
- 新增 `maxTokens` 参数（默认 512 或 1024）
- 使用 `TokenCostEstimator` 的字符/token 比率做估算

**涉及文件：**
- `knowledge/service/DocumentChunkingService.java`

---

### 2.3 分块元数据增强 [P2]

**现状：** chunk 只存 content + hash + charCount + tokenCount，无上下文信息。

**方案：** 为每个 chunk 追加元数据，存入 vector metadata

```
- headingPath: 标题路径（如 "第一章 > 第二节 > 小标题"）
- docType: 文档类型（pdf/docx/md/txt）
- prevSummary: 前一个 chunk 的摘要（1-2 句）
- nextSummary: 后一个 chunk 的摘要（1-2 句）
- keywords: 关键词/实体标签
```

**收益：** 检索时可用于元数据过滤，reranking 时可利用上下文信息

**涉及文件：**
- `knowledge/service/impl/KnowledgeBaseServiceImpl.java`
- `knowledge/service/DocumentChunkingService.java`

---

### 2.4 文档解析增强 [P2/P3]

**现状：** PDF 无 OCR、Markdown 丢失结构、无 HTML/Excel 支持。

| 改进项 | 优先级 | 方案 |
|---|---|---|
| Markdown 结构保留 | P2 | 改写 parser，保留标题层级、代码块标记 |
| HTML 支持 | P2 | 新增 `HtmlDocumentParser`，使用 Jsoup |
| Excel 支持 | P2 | 新增 `ExcelDocumentParser`，使用 Apache POI |
| PDF OCR | P3 | 集成 Tesseract 或 PaddleOCR |
| 表格结构化 | P3 | PDF/Word 表格提取为结构化数据 |

**涉及文件：**
- `rag/parser/` 目录下新增 parser 实现
- `rag/parser/DocumentParserFactory.java`

---

### 2.5 去重与增量更新 [P2]

**现状：** `contentHash` 已存储但未使用。重新处理文档时全量删除再全量插入。

**方案：**
- 重新处理时，对比新旧 chunk 的 contentHash
- 内容未变的 chunk 保留原有 embedding，不重新调用
- 新增/变化的 chunk 才执行 embed + store
- 删除不再存在的 chunk

**涉及文件：**
- `knowledge/service/impl/KnowledgeBaseServiceImpl.java`

---

## 第三阶段：生成质量与可观测性

### 3.1 Prompt 工程优化 [P1]

**现状：** Prompt 硬编码中文模板，无法定制。无 few-shot，无 CoT。

**优化方向：**

| 改进项 | 说明 |
|---|---|
| Prompt 外部化 | 移入配置文件或数据库，支持按知识库配置不同 prompt |
| Few-shot 示例 | 在 prompt 中加入 1-2 个回答范例 |
| Chain-of-Thought | 引导 LLM 先分析证据再回答 |
| 置信度输出 | 要求 LLM 输出回答置信度（高/中/低） |

**涉及文件：**
- `rag/query/DefaultRagQueryService.java`
- `knowledge/dao/entity/KnowledgeBaseEntity.java`（新增 promptTemplate 字段）
- 新增 PromptTemplateService

---

### 3.2 回答质量校验 [P3]

**现状：** 无幻觉检测，无 grounding 验证。citation 只是检索结果的直接透传。

| 机制 | 说明 |
|---|---|
| Grounding Check | 对比回答中的关键声明与检索到的 evidence |
| Citation Extraction | 从回答文本中提取 `[1]`、`[2]` 标记，与检索结果交叉验证 |
| Faithfulness Score | 用 LLM 自评回答是否忠于证据 |

**涉及文件：**
- `rag/query/DefaultRagQueryService.java`
- 新增 `rag/query/AnswerVerificationService.java`

---

### 3.3 缓存层 [P2]

**现状：** 每次查询都重新 embedding + 检索，无任何缓存。

| 缓存层级 | 策略 | 存储 |
|---|---|---|
| Query Embedding | 相同 query 文本的 embedding | Caffeine（本地内存，TTL 5min） |
| 检索结果 | 相同 query + kbId 的检索结果 | Caffeine（本地内存，TTL 2min） |
| LLM 回答 | 完全相同的输入 | Redis（可选，跨实例共享） |

**涉及文件：**
- `rag/query/DefaultRagQueryService.java`
- 新增 `rag/query/RagCacheService.java`

---

### 3.4 反馈闭环 [P2]

**现状：** 无用户反馈机制，无法评估回答质量。

**方案：**
- `RagQueryResult` 增加 `feedbackToken` 字段
- 新增 `FeedbackService`，提供 submit(feedbackToken, rating, comment) 接口
- 前端展示 👍/👎 按钮
- 定期分析低分查询，优化改写/检索策略
- rageval 模块可基于反馈数据生成质量报告

**涉及文件：**
- `rag/query/RagQueryResult.java`
- 新增 `rag/service/FeedbackService.java`
- 新增 `rag/dao/entity/FeedbackEntity.java`
- 新增 `rag/controller/FeedbackController.java`

---

### 3.5 嵌入模型缓存 [P2]

**现状：** 文档重新处理时，即使内容未变也重新生成 embedding。

**方案：**
- 利用已有的 `contentHash` 字段
- 处理前查询已有 chunk 的 hash，相同则复用已有 embedding
- 仅对变化的 chunk 调用 embedding API

**涉及文件：**
- `knowledge/service/impl/KnowledgeBaseServiceImpl.java`

---

## 配置项扩展清单

以下配置项需新增到 `RagProperties` 或知识库级别：

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `reranker-type` | enum | `lexical` | 重排器类型：lexical / cross-encoder |
| `reranker-lexical-weight` | double | 0.8 | 词法重排权重（当前硬编码） |
| `max-tokens-per-chunk` | int | 512 | 分块最大 token 数 |
| `query-cache-enabled` | boolean | false | 是否启用查询缓存 |
| `query-cache-ttl-seconds` | int | 300 | 缓存过期时间 |
| `multi-query-enabled` | boolean | false | 是否启用多查询改写 |
| `multi-query-count` | int | 3 | 多查询生成数量 |
| `prompt-template` | String | (内置) | Prompt 模板（支持变量替换） |

---

## 优先级总览

| 优先级 | 编号 | 优化项 | 预期收益 | 工作量 |
|---|---|---|---|---|
| **P0** | 1.1 | RRF 融合算法 | 检索准确率 ↑ | 小 |
| **P0** | 1.2 | Cross-Encoder Reranker | Top-K 相关性 ↑↑ | 中 |
| **P1** | 1.3 | Multi-Query 改写 | 召回率 ↑ | 中 |
| **P1** | 1.4 | 按知识库阈值配置 | 灵活性 ↑ | 小 |
| **P1** | 2.1 | 分块策略扩展 | 分块质量 ↑ | 中 |
| **P1** | 2.2 | Token 感知分块 | 避免截断 | 小 |
| **P1** | 3.1 | Prompt 外部化 + 优化 | 回答质量 ↑ | 小 |
| **P2** | 2.3 | 分块元数据增强 | 检索精度 ↑ | 中 |
| **P2** | 2.4 | 文档解析扩展 | 格式支持 ↑ | 中 |
| **P2** | 2.5 | 去重与增量更新 | 成本 ↓ 效率 ↑ | 中 |
| **P2** | 3.3 | 缓存层 | 延迟 ↓ 成本 ↓ | 中 |
| **P2** | 3.4 | 反馈闭环 | 长期质量 ↑ | 大 |
| **P2** | 3.5 | 嵌入缓存 | 成本 ↓ | 小 |
| **P3** | 2.4 | PDF OCR | 扫描件支持 | 大 |
| **P3** | 3.2 | Grounding 验证 | 可信度 ↑ | 中 |
