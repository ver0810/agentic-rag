# 文档解析模块优化设计方案

## 一、现状分析

### 1.1 当前项目架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Java Spring Boot                         │
├─────────────────────────────────────────────────────────────────┤
│  DocumentParserFactory                                          │
│      ├── PythonPdfDocumentParser (ProcessBuilder → Python)      │
│      ├── WordDocumentParser (Apache POI)                        │
│      ├── MarkdownDocumentParser (CommonMark)                    │
│      ├── HtmlDocumentParser (Jsoup)                             │
│      └── TxtDocumentParser                                      │
├─────────────────────────────────────────────────────────────────┤
│  scripts/pdf_pipeline/main.py (基于规则的布局分析)              │
└─────────────────────────────────────────────────────────────────┘
```

**当前痛点**：
- PDF 解析仅基于字体大小和规则，缺乏深度学习模型支持
- 不支持扫描版 PDF（无 OCR 能力）
- 表格识别能力有限
- 不支持图片中的文字提取
- 文档格式支持有限

### 1.2 RAGFlow 优势

```
┌─────────────────────────────────────────────────────────────────┐
│                        RAGFlow 解析架构                         │
├─────────────────────────────────────────────────────────────────┤
│  深度学习模型层                                                  │
│      ├── OCR 模型 (文本检测 + 识别)                             │
│      ├── LayoutRecognizer (布局识别 - YOLO)                     │
│      ├── TableStructureRecognizer (表格结构识别)                │
│      └── VisionFigureParser (图像描述)                          │
├─────────────────────────────────────────────────────────────────┤
│  多格式解析器                                                    │
│      ├── PDF (深度学习 + OCR)                                   │
│      ├── Word/Excel/PPT                                         │
│      ├── 图片/扫描件                                            │
│      └── 20+ 种格式                                             │
├─────────────────────────────────────────────────────────────────┤
│  REST API + Python SDK                                          │
└─────────────────────────────────────────────────────────────────┘
```

## 二、优化方案设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     AgenticRAG Java 服务                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  DocumentParserFactory (增强版)                          │   │
│  │      ├── PythonDocumentParser (HTTP Client → Python)     │   │
│  │      ├── LegacyPdfParser (保留原有实现，降级方案)        │   │
│  │      └── 其他解析器 (Word, Markdown, HTML, TXT)          │   │
│  └─────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ REST API (HTTP/JSON)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Python 文档解析微服务                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  FastAPI 服务 (doc_parser_service)                       │   │
│  │      ├── /api/v1/parse - 文档解析                        │   │
│  │      ├── /api/v1/parse/async - 异步解析                  │   │
│  │      ├── /api/v1/status/{task_id} - 任务状态             │   │
│  │      └── /health - 健康检查                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  解析引擎层                                               │   │
│  │      ├── DeepDocEngine (集成 RAGFlow 深度学习模型)       │   │
│  │      │   ├── OCR (文本检测 + 识别)                       │   │
│  │      │   ├── LayoutRecognizer (布局识别)                 │   │
│  │      │   └── TableStructureRecognizer (表格识别)         │   │
│  │      ├── MinerUEngine (MinerU 解析器)                    │   │
│  │      ├── DoclingEngine (Docling 解析器)                  │   │
│  │      └── FallbackEngine (规则引擎，降级方案)             │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  格式处理器                                               │   │
│  │      ├── PDFProcessor                                     │   │
│  │      ├── WordProcessor (python-docx)                     │   │
│  │      ├── ExcelProcessor (pandas/openpyxl)                │   │
│  │      ├── PowerPointProcessor (python-pptx)               │   │
│  │      ├── ImageProcessor (Pillow + OCR)                   │   │
│  │      └── TextProcessor (Markdown/HTML/TXT)               │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 技术选型

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| Web 框架 | FastAPI | 高性能异步框架，自动生成 API 文档 |
| 深度学习推理 | ONNX Runtime | 跨平台，支持 GPU 加速 |
| OCR 引擎 | PaddleOCR / EasyOCR | 中英文 OCR 支持 |
| PDF 处理 | PyMuPDF (fitz) | 高性能 PDF 渲染和提取 |
| Word 处理 | python-docx | Word 文档解析 |
| Excel 处理 | pandas + openpyxl | 表格数据处理 |
| 图像处理 | Pillow + OpenCV | 图像预处理 |
| 任务队列 | Celery + Redis | 异步任务处理 |
| 模型管理 | Hugging Face Hub | 模型下载和版本管理 |

### 2.3 数据结构设计

#### Python 端数据结构

```python
# models/response.py
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from enum import Enum

class BlockType(str, Enum):
    TITLE = "title"
    TEXT = "text"
    TABLE = "table"
    FIGURE = "figure"
    LIST = "list"
    CAPTION = "caption"
    HEADER = "header"
    FOOTER = "footer"
    FORMULA = "formula"

class BoundingBox(BaseModel):
    x0: float
    y0: float
    x1: float
    y1: float

class LayoutBlock(BaseModel):
    id: str
    type: BlockType
    bbox: BoundingBox
    text: Optional[str] = None
    html: Optional[str] = None  # 表格的 HTML 表示
    markdown: Optional[str] = None  # 表格的 Markdown 表示
    page_num: int
    column_index: int = 0
    confidence: float = 1.0
    attributes: Dict[str, Any] = {}

class LogicalSegment(BaseModel):
    id: str
    type: str  # heading, paragraph, list, table, caption, figure
    content: str
    heading_path: Optional[str] = None
    start_order: int
    end_order: int
    metadata: Dict[str, Any] = {}

class PageDebugInfo(BaseModel):
    page_num: int
    column_count: int
    blocks: List[LayoutBlock]
    metadata: Dict[str, Any] = {}

class DocumentMetadata(BaseModel):
    filename: str
    file_type: str
    page_count: int
    language: str = "zh"
    parse_strategy: str = "smart"
    parser_engine: str = "deepdoc"

class ParseResult(BaseModel):
    success: bool
    segments: List[LogicalSegment]
    pages: List[PageDebugInfo]
    document_metadata: DocumentMetadata
    error_message: Optional[str] = None
    processing_time_ms: int = 0

class ParseRequest(BaseModel):
    file_path: Optional[str] = None
    file_url: Optional[str] = None
    strategy: str = "smart"
    language: str = "zh"
    options: Dict[str, Any] = {}

class AsyncTaskResponse(BaseModel):
    task_id: str
    status: str  # pending, processing, completed, failed
    message: Optional[str] = None

class TaskStatusResponse(BaseModel):
    task_id: str
    status: str
    progress: float = 0.0
    result: Optional[ParseResult] = None
    error_message: Optional[str] = None
```

#### Java 端适配

现有数据结构 `StructuredParseResult`, `LogicalSegment`, `LayoutBlock`, `PageDebugInfo` 可以直接复用，只需添加 HTTP 客户端调用 Python 服务。

### 2.4 API 设计

#### Python 服务 API

```yaml
# 同步解析
POST /api/v1/parse
Content-Type: multipart/form-data

Request:
  file: [binary]
  strategy: "smart"  # naive, smart, paper, manual, table, one
  language: "zh"
  options: {"render_dpi": 216}

Response:
  {
    "success": true,
    "segments": [...],
    "pages": [...],
    "document_metadata": {...},
    "processing_time_ms": 1234
  }

# 异步解析
POST /api/v1/parse/async
Content-Type: multipart/form-data

Response:
  {
    "task_id": "uuid-xxx",
    "status": "pending"
  }

# 查询任务状态
GET /api/v1/status/{task_id}

Response:
  {
    "task_id": "uuid-xxx",
    "status": "completed",
    "progress": 1.0,
    "result": {...}
  }

# 健康检查
GET /health

Response:
  {
    "status": "healthy",
    "version": "1.0.0",
    "models_loaded": true
  }
```

### 2.5 目录结构

```
D:\CodeAndProjects\agenticrag\
├── doc-parser-service/                 # Python 文档解析微服务
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                     # FastAPI 入口
│   │   ├── config.py                   # 配置管理
│   │   ├── api/
│   │   │   ├── __init__.py
│   │   │   ├── parse.py                # 解析 API 路由
│   │   │   └── health.py               # 健康检查路由
│   │   ├── core/
│   │   │   ├── __init__.py
│   │   │   ├── parser_factory.py       # 解析器工厂
│   │   │   ├── base_parser.py          # 解析器基类
│   │   │   └── task_manager.py         # 异步任务管理
│   │   ├── engines/
│   │   │   ├── __init__.py
│   │   │   ├── deepdoc_engine.py       # RAGFlow 深度学习引擎
│   │   │   ├── ocr_engine.py           # OCR 引擎
│   │   │   ├── layout_engine.py        # 布局识别引擎
│   │   │   ├── table_engine.py         # 表格识别引擎
│   │   │   └── fallback_engine.py      # 规则引擎（降级）
│   │   ├── processors/
│   │   │   ├── __init__.py
│   │   │   ├── pdf_processor.py        # PDF 处理器
│   │   │   ├── word_processor.py       # Word 处理器
│   │   │   ├── excel_processor.py      # Excel 处理器
│   │   │   ├── ppt_processor.py        # PPT 处理器
│   │   │   ├── image_processor.py      # 图片处理器
│   │   │   └── text_processor.py       # 文本处理器
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── request.py              # 请求模型
│   │   │   ├── response.py             # 响应模型
│   │   │   └── internal.py             # 内部模型
│   │   └── utils/
│   │       ├── __init__.py
│   │       ├── pdf_utils.py            # PDF 工具
│   │       ├── image_utils.py          # 图像工具
│   │       └── text_utils.py           # 文本工具
│   ├── models/                         # 模型文件目录
│   │   ├── layout/
│   │   │   └── layout.onnx
│   │   ├── ocr/
│   │   │   ├── det.onnx
│   │   │   └── rec.onnx
│   │   └── table/
│   │       └── table_structure.onnx
│   ├── tests/
│   │   ├── test_parser.py
│   │   ├── test_ocr.py
│   │   └── test_api.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
├── bootstrap/                          # Java 主应用
│   └── src/main/java/com/agenticrag/rag/parser/
│       ├── DocumentParser.java
│       ├── DocumentParserFactory.java
│       ├── PythonDocumentParser.java   # 新增：HTTP 客户端解析器
│       ├── PythonPdfDocumentParser.java # 保留：降级方案
│       └── ...
└── docs/
    └── DOCUMENT_PARSER_OPTIMIZATION_PLAN.md
```

## 三、实现步骤

### Phase 1: Python 微服务基础框架 (1 周)

1. **创建项目结构**
   - 初始化 FastAPI 项目
   - 配置依赖管理 (requirements.txt / pyproject.toml)
   - 设置开发环境

2. **实现基础 API**
   - 健康检查接口
   - 同步解析接口框架
   - 异步解析接口框架
   - 请求/响应模型定义

3. **实现 PDF 基础处理**
   - PyMuPDF 集成
   - 原生文本提取
   - 页面渲染

### Phase 2: 深度学习模型集成 (2 周)

4. **集成 OCR 模型**
   - 下载预训练模型 (PaddleOCR / EasyOCR)
   - 实现 OCR 引擎
   - 文本检测和识别

5. **集成布局识别模型**
   - 从 RAGFlow 移植 LayoutRecognizer
   - 模型转换和优化
   - 布局分析流程

6. **集成表格识别模型**
   - 表格结构识别
   - 表格转 Markdown/HTML
   - 合并单元格处理

### Phase 3: 多格式支持 (1 周)

7. **Word 文档处理**
   - python-docx 集成
   - 样式提取
   - 表格和图片处理

8. **Excel/PPT 处理**
   - pandas/openpyxl 集成
   - python-pptx 集成

9. **图片处理**
   - 直接 OCR 处理图片
   - 图像预处理

### Phase 4: Java 集成 (1 周)

10. **Java HTTP 客户端**
    - 创建 PythonDocumentParser
    - HTTP 调用封装
    - 错误处理和降级

11. **配置管理**
    - application.yml 配置 Python 服务地址
    - 超时和重试配置
    - 降级策略配置

12. **测试和优化**
    - 集成测试
    - 性能测试
    - 错误处理优化

### Phase 5: 生产就绪 (1 周)

13. **容器化部署**
    - Dockerfile 编写
    - docker-compose 配置
    - 模型文件管理

14. **监控和日志**
    - 结构化日志
    - 性能指标
    - 错误追踪

15. **文档完善**
    - API 文档
    - 部署文档
    - 使用指南

## 四、与现有系统的集成方案

### 4.1 Java 端改动

#### 新增 PythonDocumentParser.java

```java
package com.agenticrag.rag.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Component
public class PythonDocumentParser implements DocumentParser {
    
    @Value("${agenticrag.parser.python.service-url:http://localhost:8000}")
    private String serviceUrl;
    
    @Value("${agenticrag.parser.python.timeout:300000}")
    private int timeout;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public PythonDocumentParser(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public StructuredParseResult parseStructured(Path filePath, String strategy) {
        try {
            // 构建请求
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(filePath));
            body.add("strategy", strategy);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            
            // 调用 Python 服务
            ResponseEntity<String> response = restTemplate.exchange(
                serviceUrl + "/api/v1/parse",
                HttpMethod.POST,
                request,
                String.class
            );
            
            // 解析响应
            return objectMapper.readValue(response.getBody(), StructuredParseResult.class);
            
        } catch (Exception e) {
            // 降级到原有解析器
            return fallbackParse(filePath, strategy);
        }
    }
    
    @Override
    public boolean supports(String fileExtension, String strategy) {
        // 支持所有格式
        return true;
    }
    
    @Override
    public int order() {
        return 100; // 最高优先级
    }
    
    private StructuredParseResult fallbackParse(Path filePath, String strategy) {
        // 调用原有解析器作为降级方案
        // ...
    }
}
```

#### application.yml 配置

```yaml
agenticrag:
  parser:
    python:
      service-url: ${PYTHON_PARSER_SERVICE_URL:http://localhost:8000}
      timeout: ${PYTHON_PARSER_TIMEOUT:300000}
      fallback-enabled: true  # 启用降级
      health-check-interval: 30000  # 健康检查间隔
    pdf:
      python:
        command: python
        script: scripts/pdf_pipeline/main.py
        render-dpi: 216
```

### 4.2 降级策略

```java
@Component
public class DocumentParserFactory {
    
    @Autowired
    private List<DocumentParser> parsers;
    
    @Autowired(required = false)
    private PythonDocumentParser pythonParser;
    
    public DocumentParser getParser(String fileExtension, String strategy) {
        // 1. 优先尝试 Python 服务
        if (pythonParser != null && isPythonServiceHealthy()) {
            return pythonParser;
        }
        
        // 2. 降级到原有解析器
        return parsers.stream()
            .filter(p -> p.supports(fileExtension, strategy))
            .max(Comparator.comparingInt(DocumentParser::order))
            .orElseThrow(() -> new DocumentParseException("No parser available"));
    }
    
    private boolean isPythonServiceHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                pythonServiceUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
```

## 五、模型管理策略

### 5.1 模型下载和缓存

```python
# app/config.py
from pathlib import Path

class ModelConfig:
    MODEL_DIR = Path("models")
    
    # 模型配置
    LAYOUT_MODEL_URL = "https://huggingface.co/infiniflow/layout/resolve/main/layout.onnx"
    OCR_DET_MODEL_URL = "https://huggingface.co/infiniflow/ocr/resolve/main/det.onnx"
    OCR_REC_MODEL_URL = "https://huggingface.co/infiniflow/ocr/resolve/main/rec.onnx"
    TABLE_MODEL_URL = "https://huggingface.co/infiniflow/table/resolve/main/table.onnx"
    
    @classmethod
    def ensure_models(cls):
        """确保模型文件存在，不存在则下载"""
        for model_name, url in [
            ("layout/layout.onnx", cls.LAYOUT_MODEL_URL),
            ("ocr/det.onnx", cls.OCR_DET_MODEL_URL),
            ("ocr/rec.onnx", cls.OCR_REC_MODEL_URL),
            ("table/table.onnx", cls.TABLE_MODEL_URL),
        ]:
            model_path = cls.MODEL_DIR / model_name
            if not model_path.exists():
                cls.download_model(url, model_path)
    
    @staticmethod
    def download_model(url: str, path: Path):
        """下载模型文件"""
        path.parent.mkdir(parents=True, exist_ok=True)
        # 使用 huggingface_hub 或直接下载
        import urllib.request
        urllib.request.urlretrieve(url, str(path))
```

### 5.2 模型加载优化

```python
# app/core/model_manager.py
import onnxruntime as ort
from functools import lru_cache

class ModelManager:
    _instance = None
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._models = {}
        return cls._instance
    
    @lru_cache(maxsize=1)
    def get_layout_session(self):
        """获取布局识别模型会话"""
        return ort.InferenceSession("models/layout/layout.onnx")
    
    @lru_cache(maxsize=1)
    def get_ocr_det_session(self):
        """获取 OCR 检测模型会话"""
        return ort.InferenceSession("models/ocr/det.onnx")
    
    @lru_cache(maxsize=1)
    def get_ocr_rec_session(self):
        """获取 OCR 识别模型会话"""
        return ort.InferenceSession("models/ocr/rec.onnx")
    
    @lru_cache(maxsize=1)
    def get_table_session(self):
        """获取表格识别模型会话"""
        return ort.InferenceSession("models/table/table.onnx")
```

## 六、性能优化建议

### 6.1 并发处理

```python
# 使用异步处理
import asyncio
from concurrent.futures import ProcessPoolExecutor

class AsyncParserService:
    def __init__(self):
        self.executor = ProcessPoolExecutor(max_workers=4)
    
    async def parse_async(self, file_path: str, strategy: str):
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            self.executor, 
            self.parse_sync, 
            file_path, 
            strategy
        )
        return result
```

### 6.2 缓存策略

```python
# 使用 Redis 缓存解析结果
import redis
import hashlib
import json

class ParseCache:
    def __init__(self):
        self.redis = redis.Redis(host='localhost', port=6379, db=0)
        self.ttl = 3600  # 1 小时
    
    def get_cache_key(self, file_path: str, strategy: str) -> str:
        with open(file_path, 'rb') as f:
            file_hash = hashlib.md5(f.read()).hexdigest()
        return f"parse:{file_hash}:{strategy}"
    
    def get(self, file_path: str, strategy: str):
        key = self.get_cache_key(file_path, strategy)
        cached = self.redis.get(key)
        if cached:
            return json.loads(cached)
        return None
    
    def set(self, file_path: str, strategy: str, result: dict):
        key = self.get_cache_key(file_path, strategy)
        self.redis.setex(key, self.ttl, json.dumps(result))
```

### 6.3 GPU 加速

```python
# ONNX Runtime GPU 配置
import onnxruntime as ort

def get_session_options():
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    
    # GPU 配置
    providers = ['CUDAExecutionProvider', 'CPUExecutionProvider']
    
    return options, providers
```

## 七、部署方案

### 7.1 Docker 部署

```dockerfile
# doc-parser-service/Dockerfile
FROM python:3.10-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY app/ ./app/
COPY models/ ./models/

# 暴露端口
EXPOSE 8000

# 启动命令
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 7.2 Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  doc-parser:
    build: ./doc-parser-service
    ports:
      - "8000:8000"
    volumes:
      - ./uploads:/app/uploads
      - model-cache:/app/models
    environment:
      - REDIS_URL=redis://redis:6379
    depends_on:
      - redis
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  model-cache:
```

## 八、测试策略

### 8.1 单元测试

```python
# tests/test_parser.py
import pytest
from app.engines.deepdoc_engine import DeepDocEngine

def test_pdf_parsing():
    engine = DeepDocEngine()
    result = engine.parse("tests/fixtures/test.pdf", strategy="smart")
    assert result.success
    assert len(result.segments) > 0

def test_ocr():
    engine = DeepDocEngine()
    result = engine.parse("tests/fixtures/scanned.pdf", strategy="smart")
    assert result.success
    # 验证 OCR 提取的文本
    assert any("test" in seg.content.lower() for seg in result.segments)
```

### 8.2 集成测试

```python
# tests/test_api.py
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_parse_endpoint():
    with open("tests/fixtures/test.pdf", "rb") as f:
        response = client.post(
            "/api/v1/parse",
            files={"file": ("test.pdf", f, "application/pdf")},
            data={"strategy": "smart"}
        )
    assert response.status_code == 200
    result = response.json()
    assert result["success"] is True
```

## 九、风险和缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 模型文件过大 | 部署困难 | 使用模型压缩、增量下载 |
| GPU 资源不足 | 性能下降 | CPU 降级方案、模型量化 |
| Python 服务不可用 | 解析失败 | Java 端降级到原有解析器 |
| 内存占用过高 | 服务崩溃 | 限制并发数、及时释放资源 |
| 模型加载慢 | 首次请求慢 | 预热加载、缓存模型 |

## 十、后续扩展

1. **支持更多格式**: EPUB、音频转文字、视频字幕提取
2. **多语言支持**: 日文、韩文、阿拉伯文 OCR
3. **智能分块**: 基于语义的智能文档分块
4. **元数据增强**: 自动提取文档标题、作者、日期等
5. **质量评估**: 文档解析质量评分和反馈
