# RAG 进阶与代理化路线图 (RAG Advanced & Agentic Roadmap)

本方案旨在将项目从基础的 RAG 提升至工业级、智能化的 Agentic RAG 系统。

## 第一阶段：文档智能解析与语义闭环 (Document Intelligence)
- [ ] **1.1 布局感知解析 (DeepDOC Vision-based Parsing)**
    - **详细设计文档**：参见 [DEEPDOC_DESIGN.md](docs/DEEPDOC_DESIGN.md)
    - **核心逻辑集成**：参考 DeepDOC 10 步流水线。
- [ ] **1.2 多场景专业解析器 (Specialized Domain Parsers)**
    - **策略化路由**：支持基于 (扩展名 + 场景策略) 的动态解析分派。
    - **实现以下专业解析模式**：
        - `paper`: 论文模式（双栏检测、摘要、层级标题）。
        - `manual`: 手册模式（PDF 书签提取、大纲树）。
        - `laws`: 法律模式（编章节条树状结构）。
        - `qa`: 问答模式（Q&A 自动对齐）。
        - `table`: 表格模式（结构化单元格推断）。
        - `resume`: 简历模式（5 阶段简历流水线）。
        - `email`: 邮件模式（multipart/附件递归解析）。
        - 其他：`book`, `picture`, `presentation`, `audio`, `tag` 等。
- [ ] **1.3 语义分块与层级合并 (Hierarchical Chunking)**
- [ ] **1.3 增量更新与生命周期管理**
    - 支持对已存在文档的智能覆盖更新。
    - 实现陈旧索引的自动清理机制。

## 第二阶段：检索质量的极致优化 (High-Performance Retrieval)
- [ ] **2.1 混合检索 (Hybrid Search)**
    - 集成 `pgvector` 语义检索与 PostgreSQL 全文索引（BM25 逻辑）。
    - 实现倒排索引与向量索引的权重融合 (RRF)。
- [ ] **2.2 父子文档检索 (Small-to-Big Retrieval)**
    - 存储短小的 Chunk 用于检索匹配。
    - 检索命中后自动向上回溯提取完整的上下文段落。
- [ ] **2.3 动态重排 (Rerank) 调优**
    - 优化 `CrossEncoderRerankerAdapter`。
    - 根据历史 Trace 数据自动动态调整检索数量 (Top-K)。

## 第三阶段：Agentic 逻辑实现 (Agentic Workflow)
- [ ] **3.1 查询变换 (Query Transformation)**
    - **Multi-Query**: 自动扩展用户问题以覆盖更多检索维度。
    - **HyDE**: 使用假设性文档嵌入增强跨领域检索精度。
- [ ] **3.2 自我纠正逻辑 (Self-RAG)**
    - 增加“检索内容评估”步骤，识别无关内容。
    - 实现检索失败后的自主回退或查询词修正逻辑。
- [ ] **3.3 插件化工具调用 (Tool Use)**
    - 允许 Agent 自动选择检索知识库还是调用外部计算/搜索工具。

## 第四阶段：工业级 UX 与 闭环评估 (UX & Eval)
- [ ] **4.1 流式渲染与深度溯源**
    - 实现后端 SSE (Server-Sent Events) 流式响应。
    - 前端支持文档预览高亮，精准定位引用源。
- [ ] **4.2 自动化评测体系 (RAGas Integration)**
    - 集成 RAGas 核心指标（忠实度、答案相关度、检索精确度）。
    - 实现基于评测结果的自动化回归测试。
