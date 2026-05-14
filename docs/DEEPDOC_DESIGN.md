# DeepDOC Java 视觉解析方案设计

本文定义在 Java 环境下复现 DeepDOC 风格文档视觉解析流水线的设计蓝图，目标不是逐字复刻某个开源实现，而是在当前项目架构中落地“可运行、可维护、可降级”的结构化文档理解能力。

## 1. 设计目标

### 1.1 核心目标

- 将 PDF 等复杂文档解析为结构化语义片段，而不是单一长字符串。
- 保留阅读顺序、标题层级、表格结构、图像区域和版面信息。
- 为 chunking、检索、引用定位和前端高亮提供可追踪坐标与元数据。
- 在模型不可用或性能不满足要求时，支持规则化降级。

### 1.2 非目标

- 当前阶段不追求所有版面类别的 SOTA 精度。
- 不在第一版内处理手写体、复杂公式语义理解、跨页图表关联等高难问题。
- 不把视觉解析直接耦合到最终 chunk 逻辑，二者保持可替换边界。

## 2. 总体架构

## 2.1 分层结构

```text
DocumentStorage
  -> DeepDocParser
    -> PageRenderer
    -> OcrEngine
    -> LayoutDetector
    -> TableStructureRecognizer
    -> ReadingOrderResolver
    -> LogicalSegmentBuilder
  -> SpecializedChunker
  -> KnowledgeChunk Persistence
```

### 2.2 模块职责

| 模块 | 职责 | 输出 |
|---|---|---|
| `PageRenderer` | 将 PDF 页面渲染为图像并维护缩放因子 | `RenderedPage` |
| `OcrEngine` | 识别文字块与文字框 | `OcrTextBox[]` |
| `LayoutDetector` | 检测标题、正文、图、表、页眉页脚等 | `LayoutBlock[]` |
| `TableStructureRecognizer` | 将表格区域恢复为结构化行列 | `TableBlock` |
| `ReadingOrderResolver` | 计算多栏、多块阅读顺序 | 有序块序列 |
| `LogicalSegmentBuilder` | 生成适合 chunking 的逻辑片段 | `LogicalSegment[]` |
| `SpecializedChunker` | 按场景策略进行二次分块/合并 | `KnowledgeChunk[]` |

## 3. 技术栈选型

| 维度 | 技术组件 | 说明 |
|---|---|---|
| PDF 渲染 | Apache PDFBox | 页面转图像，控制 DPI 与裁剪 |
| 模型推理 | ONNX Runtime | Java 侧统一推理运行时 |
| 张量处理 | DJL | 简化模型前后处理 |
| 聚类/统计 | Apache Commons Math | 多栏检测、简单聚类分析 |
| 段落分类 | XGBoost4J | 段落拼接、合并决策 |
| OCR | PaddleOCR 或可替换 OCR 端口 | 检测 + 识别 |

建议所有模型侧能力通过端口抽象，避免业务层直接依赖具体模型实现。

## 4. 十步流水线设计

## 4.1 第一阶段：视觉感知

### 4.1.1 `images()`

- 使用 `PDFRenderer` 以 200-300 DPI 渲染页面。
- 记录原始页面尺寸与缩放比例。
- 对超大页面支持分块渲染，避免内存峰值过高。

### 4.1.2 `ocr()`

- 识别文本内容、文字框坐标、方向、置信度。
- 输出最细粒度文本单元，后续合并在 Java 侧完成。
- 对低置信区域保留原始框，避免过早清洗。

### 4.1.3 `_layouts_rec()`

- 输出块类型：`Text`、`Title`、`List`、`Figure`、`Table`、`Header`、`Footer`、`Caption`。
- 每个块携带 `bbox + type + confidence + pageNum`。
- 若模型未命中，则允许进入规则化 fallback。

### 4.1.4 `_table_transformer_job()`

- 对表格区域单独裁切后识别单元格。
- 生成 `rows/cols/cellSpan/html/markdown` 双表示。
- 保留原始坐标，供前端定位与引用高亮。

## 4.2 第二阶段：几何与语义重构

### 4.2.1 `_text_merge()`

- 按行内距离、字体高度、垂直重叠率进行水平合并。
- 消除 OCR 将一个句子拆成多个片段的问题。
- 规则层必须可配置，便于不同文档类型调参。

### 4.2.2 `_assign_column()`

- 对左边界、中心点或块宽度进行聚类分析。
- 输出栏数、栏边界、每块所属列。
- 对跨栏标题、宽表、整页图形保留特殊标记。

### 4.2.3 `_extract_table_figure()`

- 表格转换为结构化 HTML/Markdown。
- 图片块保留坐标与占位描述，不在当前阶段做图像 caption 生成。
- 对表格标题、图注尝试就近绑定。

### 4.2.4 `_naive_vertical_merge()`

- 基于上下距离、缩进、对齐、行高相似性做初步垂直合并。
- 产出“候选段落”而非最终段落。

### 4.2.5 `_concat_downward()`

- 使用规则 + 模型共同判断两块是否应合并。
- 建议特征包含：
  - 上下距离
  - X 轴对齐差异
  - 字体高度差异
  - 末尾标点
  - 是否同列
  - 语义相似度
  - 是否标题后首段

### 4.2.6 `_final_reading_order_merge()`

- 最终排序优先级：`Page -> Column -> Y -> X`。
- 对跨页延续段落保留 `continued=true` 标记。
- 生成最终 `LogicalSegment[]`。

## 5. 核心数据模型

```java
public class LayoutBlock {
    String id;
    String type;
    Rectangle bbox;
    String text;
    String html;
    int pageNum;
    int columnIndex;
    double confidence;
    Map<String, Object> attributes;
}

public class LogicalSegment {
    String id;
    String type;
    String content;
    String headingPath;
    int pageNum;
    int startOrder;
    int endOrder;
    Rectangle bbox;
    Map<String, Object> metadata;
}

public class PdfPageContent {
    int pageNum;
    BufferedImage image;
    List<LayoutBlock> blocks;
    int columnCount;
    double scaleFactor;
}
```

建议 `LogicalSegment` 成为 parser 与 chunker 之间的标准边界对象。

## 6. 解析策略矩阵

| 策略 | 合并方式 | 关键增强 |
|---|---|---|
| `naive` | 按长度与段落分块 | 默认兜底 |
| `paper` | 按标题层级与双栏结构合并 | 摘要、章节、参考文献识别 |
| `manual` | 按目录树与书签合并 | 章节边界稳定 |
| `laws` | 按树状法规结构合并 | “编-章-节-条”抽取 |
| `qa` | 问答配对 | 问题与答案绑定 |
| `table` | 表格逐行或逐段结构化 | 表头注入、列类型识别 |
| `resume` | 字段聚类 | 教育、经历、技能区块 |
| `one` | 全文单块 | 超短文档或调试模式 |

## 7. 接口设计建议

## 7.1 Parser 端口

```java
public interface StructuredDocumentParser {
    boolean supports(String extension, String strategy);
    StructuredParseResult parse(InputStream input, ParseContext context);
}
```

## 7.2 核心返回对象

```java
public class StructuredParseResult {
    List<LogicalSegment> segments;
    List<PageDebugInfo> pages;
    Map<String, Object> documentMetadata;
}
```

## 7.3 上下文参数

建议 `ParseContext` 至少包含：

- 文件类型
- 解析策略
- 是否开启 OCR
- 是否开启表格结构化
- 最大页数
- debug 开关

## 8. 数据流重构

当前链路通常是：

```text
File -> Parser -> PlainText -> Chunker -> Embedding
```

建议升级为：

```text
File
  -> StructuredDocumentParser
  -> List<LogicalSegment>
  -> SpecializedChunker
  -> List<KnowledgeChunk>
  -> Vector Store
```

这样可以在 chunk 侧直接使用：

- `headingPath`
- `pageNum`
- `bbox`
- `segmentType`
- `tableHtml`
- `keywords`

## 9. 降级与容错策略

视觉解析链路不能设计成“全有或全无”，需要内建降级路径：

### 9.1 模型不可用

- 布局模型不可用时，降级为 OCR + 规则分段。
- OCR 不可用时，回退到 PDFBox 文本抽取。
- 表格结构识别不可用时，保留原始表格文本与坐标。

### 9.2 结果质量过低

- 当 OCR 平均置信度低于阈值时，标记文档“解析低可信”。
- 当检测页眉页脚比例异常高时，触发清洗规则。
- 当阅读顺序冲突过多时，回退到简单排序模式。

### 9.3 性能保护

- 限制单文档最大页数。
- 超大 PDF 采用分页异步处理。
- 对高成本模型设置开关与采样策略。

## 10. 性能与资源预算

建议第一版先给出明确预算，避免解析能力上线后拖垮整体链路：

| 项目 | 建议预算 |
|---|---|
| 单页渲染耗时 | < 300ms |
| 单页 OCR 耗时 | < 800ms |
| 单页布局检测耗时 | < 500ms |
| 单文档解析内存峰值 | 可配置上限 |
| 调试图片保留 | 默认关闭，仅按需开启 |

## 11. 可观测性

建议对以下节点打点：

- 渲染耗时
- OCR 耗时
- 布局检测耗时
- 表格识别耗时
- 段落合并耗时
- 最终 segment 数量
- 各块类型数量
- OCR 平均置信度

调试模式下可附加输出：

- 页级渲染图
- 布局框叠加图
- 表格识别中间结果
- 最终阅读顺序标注图

## 12. 实施顺序建议

建议按最小闭环逐步推进：

1. `PDFRenderer + PDFBox 文本抽取` 打底，先做非视觉 fallback。
2. 接入 OCR，产出 `OcrTextBox`。
3. 接入布局检测，只支持 `Text/Title/Table/Figure` 四大类。
4. 打通 `LogicalSegment` 输出与 `headingPath` 元数据。
5. 接入表格结构识别。
6. 再做场景策略分派与 specialized chunker。

## 13. 第一版交付范围

建议第一版只承诺以下能力：

- 支持 PDF 结构化解析
- 支持标题、正文、表格、图片四类块
- 输出 `LogicalSegment[]`
- 输出页码、坐标、标题路径
- 可与现有 `DocumentChunkingService` 对接

这样可以先验证整条链路，再逐步扩充细节能力。

## 14. 结论

DeepDOC 风格解析的价值，不在于“更复杂的解析过程”本身，而在于为后续检索和生成提供更高质量、更可解释的数据基础。只要 parser 与 chunker 的边界设计清晰，并具备良好的降级与观测能力，这条路线就值得逐步落地。
