import time
import uuid
from pathlib import Path
from loguru import logger
import fitz

from app.config import settings
from app.core.base_parser import BaseParser
from app.engines.deepdoc_engine import DeepDocEngine
from app.models.response import (
    ParseResult,
    DocumentMetadata,
    LogicalSegment,
    PageDebugInfo,
    LayoutBlock,
    BlockType,
    BoundingBox,
)

# Lazy initialization
_engine: DeepDocEngine | None = None


def get_engine() -> DeepDocEngine | None:
    global _engine
    if _engine is None:
        try:
            _engine = DeepDocEngine(model_dir=settings.model_dir / "deepdoc")
            _engine.load_models()
        except Exception as e:
            logger.warning(f"Failed to load DeepDoc engine: {e}, fallback to basic mode")
            _engine = None
    return _engine


class PdfProcessor(BaseParser):
    def supports(self, file_extension: str, strategy: str) -> bool:
        return file_extension in ("pdf",)

    def order(self) -> int:
        return 10

    def parse(self, file_path: Path, strategy: str, language: str, **kwargs) -> ParseResult:
        engine = get_engine()

        if engine and engine.is_ready:
            logger.info(f"Using DeepDoc engine for {file_path.name}")
            return engine.parse_pdf(file_path, strategy, language)

        logger.info(f"Using fallback parser for {file_path.name}")
        return self._fallback_parse(file_path, strategy, language)

    def _fallback_parse(self, file_path: Path, strategy: str, language: str) -> ParseResult:
        start = time.time()
        try:
            doc = fitz.open(str(file_path))
            page_count = doc.page_count

            segments: list[LogicalSegment] = []
            pages: list[PageDebugInfo] = []

            for page_idx in range(page_count):
                page = doc[page_idx]
                page_info = self._process_page_basic(page, page_idx)
                pages.append(page_info)
                segments.extend(self._page_blocks_to_segments(page_info.blocks, page_idx))

            doc.close()

            segments.sort(key=lambda s: s.start_order)
            for i, seg in enumerate(segments):
                segments[i] = seg.model_copy(update={"start_order": i, "end_order": i})

            elapsed = int((time.time() - start) * 1000)
            return ParseResult(
                success=True,
                segments=segments,
                pages=pages,
                document_metadata=DocumentMetadata(
                    filename=file_path.name,
                    file_type="pdf",
                    page_count=page_count,
                    language=language,
                    parse_strategy=strategy,
                    parser_engine="fallback",
                ),
                processing_time_ms=elapsed,
            )
        except Exception as e:
            logger.exception("PDF parse failed")
            elapsed = int((time.time() - start) * 1000)
            return ParseResult(success=False, error_message=str(e), processing_time_ms=elapsed)

    def _process_page_basic(self, page: fitz.Page, page_idx: int) -> PageDebugInfo:
        blocks_data = page.get_text("dict", flags=fitz.TEXT_PRESERVE_WHITESPACE)
        layout_blocks: list[LayoutBlock] = []

        for block in blocks_data.get("blocks", []):
            bbox_raw = block.get("bbox", (0, 0, 0, 0))
            bbox = BoundingBox(x0=bbox_raw[0], y0=bbox_raw[1], x1=bbox_raw[2], y1=bbox_raw[3])

            if block.get("type") == 0:
                text = self._extract_block_text(block)
                if text.strip():
                    block_type = self._infer_block_type(block)
                    layout_blocks.append(LayoutBlock(
                        id=str(uuid.uuid4()),
                        type=block_type,
                        bbox=bbox,
                        text=text,
                        page_num=page_idx + 1,
                        confidence=1.0,
                    ))
            elif block.get("type") == 1:
                layout_blocks.append(LayoutBlock(
                    id=str(uuid.uuid4()),
                    type=BlockType.FIGURE,
                    bbox=bbox,
                    page_num=page_idx + 1,
                    confidence=1.0,
                ))

        return PageDebugInfo(
            page_num=page_idx + 1,
            column_count=self._detect_columns(layout_blocks),
            blocks=layout_blocks,
        )

    def _extract_block_text(self, block: dict) -> str:
        lines = []
        for line in block.get("lines", []):
            spans = line.get("spans", [])
            line_text = "".join(span.get("text", "") for span in spans)
            lines.append(line_text)
        return "\n".join(lines)

    def _infer_block_type(self, block: dict) -> BlockType:
        for line in block.get("lines", []):
            for span in line.get("spans", []):
                size = span.get("size", 10)
                if size >= 16:
                    return BlockType.TITLE
        return BlockType.TEXT

    def _detect_columns(self, blocks: list[LayoutBlock]) -> int:
        if not blocks:
            return 1
        x_positions = sorted(set(b.bbox.x0 for b in blocks))
        if len(x_positions) < 2:
            return 1
        clusters = 1
        threshold = 50
        for i in range(1, len(x_positions)):
            if x_positions[i] - x_positions[i - 1] > threshold:
                clusters += 1
        return min(clusters, 3)

    def _page_blocks_to_segments(self, blocks: list[LayoutBlock], page_idx: int) -> list[LogicalSegment]:
        segments = []
        for block in blocks:
            if block.type == BlockType.FIGURE:
                continue
            seg_type = "heading" if block.type == BlockType.TITLE else "paragraph"
            segments.append(LogicalSegment(
                id=block.id,
                type=seg_type,
                content=block.text or "",
                start_order=page_idx * 1000 + len(segments),
                end_order=page_idx * 1000 + len(segments),
            ))
        return segments
