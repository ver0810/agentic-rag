# DeepDOC Java 视觉解析方案设计 (DEEPDOC Design Blueprint)

本文件详细描述了在 Java 环境下复现 DeepDOC 10 步 PDF 解析流水线的架构设计方案。

## 1. 技术栈选型 (Technical Stack)

| 维度 | 技术组件 | 说明 |
| :--- | :--- | :--- |
| **图像处理** | Apache PDFBox (PDFRenderer) | 将 PDF 页面渲染为 BufferedImage (300DPI) |
| **模型推理引擎** | ONNX Runtime (Java API) | 高性能跨平台模型推理 |
| **张量运算** | DJL (Deep Java Library) | 提供 NDArray 接口，简化模型前后处理逻辑 |
| **机器学习** | XGBoost4J | 执行智能段落拼接决策 |
| **数学/聚类** | Apache Commons Math | 用于 KMeans 多栏布局检测 |

## 2. 10 步流水线详细设计 (The 10-Step Pipeline)

### 第一阶段：视觉感知 (Vision Perception)
1.  **`images()` (图像化渲染)**
    - 使用 PDFRenderer 将页面转为图像。记录 `ScaleFactor` 以便坐标对齐。
2.  **`ocr()` (文字识别)**
    - 集成 **PaddleOCR** (Detection + Recognition)。识别每个文本块的原始文字与像素坐标。
3.  **`_layouts_rec()` (版面分析)**
    - 运行 **YOLOv10** 模型，标注区域类型：`Text`, `Title`, `Figure`, `Table`, `Header`, `Footer`。
4.  **`_table_transformer_job()` (表格结构识别)**
    - 对 `Table` 区域运行 **Table Transformer**。识别单元格坐标，处理跨行跨列。

### 第二阶段：几何与语义重构 (Geometric & Semantic Reconstruction)
5.  **`_text_merge()` (水平合并)**
    - 逻辑：同一行内，若两个 OCR 文本框距离小于字符平均宽度的阈值，则进行合并。
6.  **`_assign_column()` (多栏检测)**
    - 算法：对所有文本块左边界坐标进行 **KMeans 聚类**（K=1~4）。确定页面分栏结构。
7.  **`_extract_table_figure()` (结构化提取)**
    - 将文字填充至 HTML `<table>` 结构中。对图片区域保留 BBox 坐标。
8.  **`_naive_vertical_merge()` (初步垂直合并)**
    - 规则：基于对齐方式、字体高度相似度，对物理相邻的行进行合并。
9.  **`_concat_downward()` (智能段落拼接)**
    - **核心**：使用 **XGBoost 模型** 判断是否拼接。
    - 特征：计算上下块的行间距、重叠率、结尾标点符号特征、语义衔接特征（共 31 维）。
10. **`_final_reading_order_merge()` (阅读顺序重构)**
    - 排序优先级：`Page > Column > Vertical Position (Y) > Horizontal Position (X)`。
    - 生成最终的结构化 Markdown 输出。

## 3. 核心数据模型 (Core Data Models)

```java
// 布局块信息
public class LayoutBlock {
    String type;        // Text, Table, Heading, etc.
    Rectangle bbox;     // 像素级坐标
    String text;        // 提取的文本内容
    String html;        // 若为 Table，存储 HTML 结构
    double confidence;  // 模型置信度
}

// 页面上下文
public class PdfPageContent {
    int pageNum;
    BufferedImage image;
    List<LayoutBlock> blocks;
    int columnCount;    // 检测到的栏数
}
```

## 5. 多场景专业解析策略 (Specialized Strategy Matrix)

系统根据用户指定的策略（Strategy）采用不同的后期处理逻辑：

| 策略 | 分块/合并模式 (Merging Mode) | 特殊处理 |
| :--- | :--- | :--- |
| **naive** | `naive_merge()` (按 Token 长度) | 默认兜底策略 |
| **paper** | `hierarchical_merge()` (按章节级次) | 摘要、标题正则、双栏检测 |
| **laws** | `tree_merge()` (树状级次合并) | 解析“编-章-节-条”特定逻辑 |
| **qa** | `pair_alignment()` (问答对对齐) | 识别问号、Bullet，每个 Q&A 一块 |
| **table** | `row_wise_merge()` (按行合并) | 表头注入、列类型推断 |
| **manual** | `outline_merge()` (按大纲合并) | 利用 PDF 目录树确定边界 |
| **resume** | `field_extraction()` (字段分块) | 姓名、经历、技能等各为一簇 |
| **one** | `full_document_merge()` | 整篇文档最终合并为唯一的一个 Chunk |

## 6. 流水线数据流向重构 (Data Flow Refactoring)

为了支持上述模式，数据流向将从 `File -> String -> Chunks` 演进为：
1.  **Raw File** -> `DeepDocParser`
2.  `DeepDocParser` -> **`List<LogicalSegment>`** (带有 Type 和 Metadata)
3.  **`List<LogicalSegment>`** -> `SpecializedChunker` (应用对应的 Merging 算法)
4.  `SpecializedChunker` -> **`List<KnowledgeChunk>`** (最终入库的向量块)

解析分派器将采用 `ParserKey(extension, strategy)` 作为查找键：
- 默认路由：`(*, naive)`
- 特定路由：`(pdf, paper)`, `(eml, email)` 等。
