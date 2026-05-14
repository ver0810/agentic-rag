from pydantic import BaseModel, Field
from typing import Optional
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
    html: Optional[str] = None
    markdown: Optional[str] = None
    page_num: int
    column_index: int = 0
    confidence: float = 1.0
    attributes: dict = Field(default_factory=dict)


class LogicalSegment(BaseModel):
    id: str
    type: str
    content: str
    heading_path: Optional[str] = None
    start_order: int
    end_order: int
    metadata: dict = Field(default_factory=dict)


class PageDebugInfo(BaseModel):
    page_num: int
    column_count: int
    blocks: list[LayoutBlock] = Field(default_factory=list)
    metadata: dict = Field(default_factory=dict)


class DocumentMetadata(BaseModel):
    filename: str
    file_type: str
    page_count: int
    language: str = "zh"
    parse_strategy: str = "smart"
    parser_engine: str = "fallback"


class ParseResult(BaseModel):
    success: bool
    segments: list[LogicalSegment] = Field(default_factory=list)
    pages: list[PageDebugInfo] = Field(default_factory=list)
    document_metadata: Optional[DocumentMetadata] = None
    error_message: Optional[str] = None
    processing_time_ms: int = 0


class AsyncTaskResponse(BaseModel):
    task_id: str
    status: str
    message: Optional[str] = None


class TaskStatusResponse(BaseModel):
    task_id: str
    status: str
    progress: float = 0.0
    result: Optional[ParseResult] = None
    error_message: Optional[str] = None


class HealthResponse(BaseModel):
    status: str
    version: str
    models_loaded: bool = False
