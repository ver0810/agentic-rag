import time
import uuid
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
import fitz
from loguru import logger

from app.engines.ocr_engine import OCR
from app.engines.layout_engine import LayoutRecognizer
from app.engines.table_engine import TableStructureRecognizer
from app.models.response import (
    ParseResult,
    DocumentMetadata,
    LogicalSegment,
    PageDebugInfo,
    LayoutBlock,
    BlockType,
    BoundingBox,
)


class DeepDocEngine:
    def __init__(self, model_dir: str | Path = "models/deepdoc", zoomin: int = 3):
        self.model_dir = Path(model_dir)
        self.zoomin = zoomin

        self.ocr = OCR(model_dir)
        self.layouter = LayoutRecognizer(model_dir)
        self.table_recognizer = TableStructureRecognizer(model_dir)

    def load_models(self):
        self.ocr.load()
        self.layouter.load()
        self.table_recognizer.load()
        logger.info("DeepDoc engine models loaded")

    @property
    def is_ready(self) -> bool:
        return self.ocr.is_loaded and self.layouter.is_loaded

    def parse_pdf(self, file_path: Path, strategy: str, language: str) -> ParseResult:
        start = time.time()

        try:
            doc = fitz.open(str(file_path))
            page_count = doc.page_count

            all_segments: list[LogicalSegment] = []
            all_pages: list[PageDebugInfo] = []

            for page_idx in range(page_count):
                page = doc[page_idx]
                page_result = self._process_page(page, page_idx, strategy)
                all_pages.append(page_result)
                all_segments.extend(self._page_to_segments(page_result, page_idx))

            doc.close()

            all_segments.sort(key=lambda s: s.start_order)
            for i, seg in enumerate(all_segments):
                all_segments[i] = seg.model_copy(update={"start_order": i, "end_order": i})

            elapsed = int((time.time() - start) * 1000)
            return ParseResult(
                success=True,
                segments=all_segments,
                pages=all_pages,
                document_metadata=DocumentMetadata(
                    filename=file_path.name,
                    file_type="pdf",
                    page_count=page_count,
                    language=language,
                    parse_strategy=strategy,
                    parser_engine="deepdoc",
                ),
                processing_time_ms=elapsed,
            )
        except Exception as e:
            import traceback
            error_detail = traceback.format_exc()
            logger.error(f"DeepDoc PDF parse failed: {type(e).__name__}: {e}\n{error_detail}")
            elapsed = int((time.time() - start) * 1000)
            return ParseResult(success=False, error_message=f"{type(e).__name__}: {e}", processing_time_ms=elapsed)

    def _process_page(self, page: fitz.Page, page_idx: int, strategy: str) -> PageDebugInfo:
        # Render page to image
        mat = fitz.Matrix(self.zoomin, self.zoomin)
        pix = page.get_pixmap(matrix=mat)
        img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)

        if pix.n == 4:  # RGBA
            img = cv2.cvtColor(img, cv2.COLOR_RGBA2RGB)
        elif pix.n == 1:  # Grayscale
            img = cv2.cvtColor(img, cv2.COLOR_GRAY2RGB)

        # Get native text from PDF
        native_blocks = self._extract_native_text(page)

        # Run OCR if needed
        ocr_results = []
        if strategy in ("smart", "paper") or not native_blocks:
            ocr_results = self.ocr(img)

        # Run layout recognition
        layout_boxes = self.layouter(img) if self.layouter.is_loaded else []

        # Combine results
        combined_blocks = self._combine_results(
            native_blocks, ocr_results, layout_boxes, page_idx, img
        )

        # Detect columns
        column_count = self._detect_columns(combined_blocks)

        return PageDebugInfo(
            page_num=page_idx + 1,
            column_count=column_count,
            blocks=combined_blocks,
        )

    def _extract_native_text(self, page: fitz.Page) -> list[dict]:
        blocks = []
        text_dict = page.get_text("dict", flags=fitz.TEXT_PRESERVE_WHITESPACE)

        for block in text_dict.get("blocks", []):
            if block.get("type") != 0:
                continue

            bbox = block.get("bbox", (0, 0, 0, 0))
            text_parts = []
            font_sizes = []

            for line in block.get("lines", []):
                for span in line.get("spans", []):
                    text_parts.append(span.get("text", ""))
                    font_sizes.append(span.get("size", 10))

            text = "".join(text_parts).strip()
            if text:
                avg_size = np.mean(font_sizes) if font_sizes else 10
                blocks.append({
                    "text": text,
                    "bbox": bbox,
                    "font_size": avg_size,
                    "source": "native",
                })

        return blocks

    def _combine_results(
        self,
        native_blocks: list[dict],
        ocr_results: list[dict],
        layout_boxes: list[dict],
        page_idx: int,
        image: np.ndarray,
    ) -> list[LayoutBlock]:
        combined: list[LayoutBlock] = []

        # Process OCR results
        for i, ocr_item in enumerate(ocr_results):
            box = np.array(ocr_item["box"])
            bbox = BoundingBox(
                x0=float(box[:, 0].min()),
                y0=float(box[:, 1].min()),
                x1=float(box[:, 0].max()),
                y1=float(box[:, 1].max()),
            )

            block_type = self._infer_type_from_layout(bbox, layout_boxes)

            combined.append(LayoutBlock(
                id=str(uuid.uuid4()),
                type=block_type,
                bbox=bbox,
                text=ocr_item["text"],
                page_num=page_idx + 1,
                confidence=ocr_item["confidence"],
                attributes={"source": "ocr"},
            ))

        # Add native text blocks not covered by OCR
        for native in native_blocks:
            nbbox = native["bbox"]
            covered = False

            for block in combined:
                if self._bbox_overlap(
                    (block.bbox.x0, block.bbox.y0, block.bbox.x1, block.bbox.y1),
                    nbbox,
                ) > 0.5:
                    covered = True
                    break

            if not covered:
                bbox = BoundingBox(x0=nbbox[0], y0=nbbox[1], x1=nbbox[2], y1=nbbox[3])
                block_type = BlockType.TITLE if native["font_size"] >= 16 else BlockType.TEXT

                combined.append(LayoutBlock(
                    id=str(uuid.uuid4()),
                    type=block_type,
                    bbox=bbox,
                    text=native["text"],
                    page_num=page_idx + 1,
                    confidence=1.0,
                    attributes={"source": "native", "font_size": native["font_size"]},
                ))

        # Sort by position
        combined.sort(key=lambda b: (b.bbox.y0, b.bbox.x0))

        # Process tables
        table_regions = [lb for lb in layout_boxes if lb["type"] == "table"]
        for table_region in table_regions:
            x1, y1, x2, y2 = [int(c) for c in table_region["bbox"]]
            table_img = image[y1:y2, x1:x2]

            if table_img.size > 0 and self.table_recognizer.is_loaded:
                table_struct = self.table_recognizer(table_img)
                table_ocr = self.ocr(table_img)

                for ocr_item in table_ocr:
                    box = np.array(ocr_item["box"])
                    box[:, 0] += x1
                    box[:, 1] += y1
                    ocr_item["box"] = box.tolist()

                table_html = self.table_recognizer.construct_html(
                    table_struct, table_ocr, image.shape
                )

                bbox = BoundingBox(x0=float(x1), y0=float(y1), x1=float(x2), y1=float(y2))
                combined.append(LayoutBlock(
                    id=str(uuid.uuid4()),
                    type=BlockType.TABLE,
                    bbox=bbox,
                    html=table_html,
                    page_num=page_idx + 1,
                    confidence=table_region["score"],
                    attributes={"source": "deepdoc"},
                ))

        # Sort final result
        combined.sort(key=lambda b: (b.bbox.y0, b.bbox.x0))
        return combined

    def _infer_type_from_layout(self, bbox: BoundingBox, layout_boxes: list[dict]) -> BlockType:
        for lb in layout_boxes:
            if self._bbox_overlap(
                (bbox.x0, bbox.y0, bbox.x1, bbox.y1),
                tuple(lb["bbox"]),
            ) > 0.5:
                lb_type = lb["type"].lower()
                if lb_type == "title":
                    return BlockType.TITLE
                elif lb_type == "figure":
                    return BlockType.FIGURE
                elif lb_type == "table":
                    return BlockType.TABLE
                elif lb_type in ("header", "footer"):
                    return BlockType(lb_type)
                elif lb_type == "equation":
                    return BlockType.FORMULA
                else:
                    return BlockType.TEXT
        return BlockType.TEXT

    def _bbox_overlap(self, box1: tuple, box2: tuple) -> float:
        x1 = max(box1[0], box2[0])
        y1 = max(box1[1], box2[1])
        x2 = min(box1[2], box2[2])
        y2 = min(box1[3], box2[3])

        if x2 <= x1 or y2 <= y1:
            return 0.0

        inter = (x2 - x1) * (y2 - y1)
        area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
        area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])

        return inter / min(area1, area2) if min(area1, area2) > 0 else 0.0

    def _detect_columns(self, blocks: list[LayoutBlock]) -> int:
        if len(blocks) < 2:
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

    def _page_to_segments(self, page_info: PageDebugInfo, page_idx: int) -> list[LogicalSegment]:
        segments = []
        order_base = page_idx * 1000

        for block in page_info.blocks:
            if block.type == BlockType.FIGURE:
                continue

            if block.type == BlockType.TABLE:
                content = block.html or ""
                seg_type = "table"
            else:
                content = block.text or ""
                seg_type = "heading" if block.type == BlockType.TITLE else "paragraph"

            if not content.strip():
                continue

            segments.append(LogicalSegment(
                id=block.id,
                type=seg_type,
                content=content,
                start_order=order_base + len(segments),
                end_order=order_base + len(segments),
                metadata={
                    "source": block.attributes.get("source", "unknown"),
                    "confidence": block.confidence,
                },
            ))

        return segments
