import time
import uuid
from pathlib import Path
from loguru import logger
from docx import Document as DocxDocument
from docx.table import Table
from docx.text.paragraph import Paragraph

from app.core.base_parser import BaseParser
from app.models.response import (
    ParseResult,
    DocumentMetadata,
    LogicalSegment,
    PageDebugInfo,
    LayoutBlock,
    BlockType,
    BoundingBox,
)


class WordProcessor(BaseParser):
    def supports(self, file_extension: str, strategy: str) -> bool:
        return file_extension in ("docx",)

    def order(self) -> int:
        return 10

    def parse(self, file_path: Path, strategy: str, language: str, **kwargs) -> ParseResult:
        start = time.time()
        try:
            doc = DocxDocument(str(file_path))

            segments: list[LogicalSegment] = []
            blocks: list[LayoutBlock] = []
            order = 0

            for element in doc.element.body:
                if element.tag.endswith("}p"):
                    para = Paragraph(element, doc)
                    text = para.text.strip()
                    if not text:
                        continue

                    style_name = para.style.name.lower() if para.style else ""
                    is_heading = "heading" in style_name
                    block_type = BlockType.TITLE if is_heading else BlockType.TEXT

                    seg_type = "heading" if is_heading else "paragraph"
                    heading_level = self._get_heading_level(style_name)

                    block_id = str(uuid.uuid4())
                    blocks.append(LayoutBlock(
                        id=block_id,
                        type=block_type,
                        bbox=BoundingBox(x0=0, y0=0, x1=100, y1=20),
                        text=text,
                        page_num=1,
                        confidence=1.0,
                        attributes={"style": style_name, "heading_level": heading_level},
                    ))

                    segments.append(LogicalSegment(
                        id=block_id,
                        type=seg_type,
                        content=text,
                        start_order=order,
                        end_order=order,
                        metadata={"style": style_name, "heading_level": heading_level},
                    ))
                    order += 1

                elif element.tag.endswith("}tbl"):
                    table = Table(element, doc)
                    table_html = self._table_to_html(table)

                    block_id = str(uuid.uuid4())
                    blocks.append(LayoutBlock(
                        id=block_id,
                        type=BlockType.TABLE,
                        bbox=BoundingBox(x0=0, y0=0, x1=100, y1=100),
                        html=table_html,
                        page_num=1,
                        confidence=1.0,
                    ))

                    segments.append(LogicalSegment(
                        id=block_id,
                        type="table",
                        content=table_html,
                        start_order=order,
                        end_order=order,
                    ))
                    order += 1

            page_info = PageDebugInfo(
                page_num=1,
                column_count=1,
                blocks=blocks,
            )

            elapsed = int((time.time() - start) * 1000)
            return ParseResult(
                success=True,
                segments=segments,
                pages=[page_info],
                document_metadata=DocumentMetadata(
                    filename=file_path.name,
                    file_type="docx",
                    page_count=1,
                    language=language,
                    parse_strategy=strategy,
                    parser_engine="python-docx",
                ),
                processing_time_ms=elapsed,
            )
        except Exception as e:
            logger.exception("Word parse failed")
            elapsed = int((time.time() - start) * 1000)
            return ParseResult(success=False, error_message=str(e), processing_time_ms=elapsed)

    def _get_heading_level(self, style_name: str) -> int:
        if "heading" not in style_name:
            return 0
        for i in range(1, 10):
            if f"heading {i}" in style_name:
                return i
        return 1

    def _table_to_html(self, table: Table) -> str:
        html = "<table>"
        for row in table.rows:
            html += "<tr>"
            for cell in row.cells:
                cell_text = " ".join(p.text for p in cell.paragraphs).strip()
                html += f"<td>{cell_text}</td>"
            html += "</tr>"
        html += "</table>"
        return html
