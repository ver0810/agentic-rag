import time
import uuid
from pathlib import Path
from loguru import logger
import cv2
import numpy as np

from app.config import settings
from app.core.base_parser import BaseParser
from app.engines.ocr_engine import OCR
from app.models.response import (
    ParseResult,
    DocumentMetadata,
    LogicalSegment,
    PageDebugInfo,
    LayoutBlock,
    BlockType,
    BoundingBox,
)

_ocr: OCR | None = None


def get_ocr() -> OCR | None:
    global _ocr
    if _ocr is None:
        try:
            _ocr = OCR(model_dir=settings.model_dir / "deepdoc")
            _ocr.load()
        except Exception as e:
            logger.warning(f"Failed to load OCR: {e}")
            _ocr = None
    return _ocr


class ImageProcessor(BaseParser):
    def supports(self, file_extension: str, strategy: str) -> bool:
        return file_extension in ("png", "jpg", "jpeg", "bmp", "tiff", "tif", "webp")

    def order(self) -> int:
        return 10

    def parse(self, file_path: Path, strategy: str, language: str, **kwargs) -> ParseResult:
        start = time.time()
        try:
            image = cv2.imread(str(file_path))
            if image is None:
                return ParseResult(
                    success=False,
                    error_message=f"Failed to read image: {file_path}",
                    processing_time_ms=0,
                )

            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            h, w = image.shape[:2]

            ocr = get_ocr()
            ocr_results = ocr(image) if ocr else []

            segments: list[LogicalSegment] = []
            blocks: list[LayoutBlock] = []
            order = 0

            for item in ocr_results:
                box = np.array(item["box"])
                bbox = BoundingBox(
                    x0=float(box[:, 0].min()),
                    y0=float(box[:, 1].min()),
                    x1=float(box[:, 0].max()),
                    y1=float(box[:, 1].max()),
                )

                block_id = str(uuid.uuid4())
                blocks.append(LayoutBlock(
                    id=block_id,
                    type=BlockType.TEXT,
                    bbox=bbox,
                    text=item["text"],
                    page_num=1,
                    confidence=item["confidence"],
                ))

                segments.append(LogicalSegment(
                    id=block_id,
                    type="paragraph",
                    content=item["text"],
                    start_order=order,
                    end_order=order,
                    metadata={"ocr_confidence": item["confidence"]},
                ))
                order += 1

            # Sort by reading order (top to bottom, left to right)
            segments.sort(key=lambda s: (
                blocks[segments.index(s)].bbox.y0 if s.id in [b.id for b in blocks] else 0
            ))
            for i, seg in enumerate(segments):
                segments[i] = seg.model_copy(update={"start_order": i, "end_order": i})

            page_info = PageDebugInfo(
                page_num=1,
                column_count=1,
                blocks=blocks,
                metadata={"image_width": w, "image_height": h},
            )

            elapsed = int((time.time() - start) * 1000)
            return ParseResult(
                success=True,
                segments=segments,
                pages=[page_info],
                document_metadata=DocumentMetadata(
                    filename=file_path.name,
                    file_type=file_path.suffix.lstrip("."),
                    page_count=1,
                    language=language,
                    parse_strategy=strategy,
                    parser_engine="ocr",
                ),
                processing_time_ms=elapsed,
            )
        except Exception as e:
            logger.exception("Image parse failed")
            elapsed = int((time.time() - start) * 1000)
            return ParseResult(success=False, error_message=str(e), processing_time_ms=elapsed)
