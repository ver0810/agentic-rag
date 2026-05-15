import argparse
import json
import math
import re
from pathlib import Path


LIST_RE = re.compile(r"^(([-*•])|(\d+[.)]))\s+.+")
CAPTION_RE = re.compile(r"^(图|表|figure|table)\s*[0-9一二三四五六七八九十IVXivx.:：-].*", re.I)
TABLE_RE = re.compile(r".*\|.*\|.*|.*\s{2,}.*\s{2,}.*")
MANUAL_HEADING_RE = re.compile(r"^(第[一二三四五六七八九十百千0-9]+[章节篇部分]\s*.*|\d+(\.\d+){0,3}\s+.+|\d+[、.)]\s*.+)$")
ABSTRACT_RE = re.compile(r"^(abstract|摘要)$", re.I)
REFERENCE_HEADING_RE = re.compile(r"^(references|参考文献)$", re.I)
REFERENCE_ENTRY_RE = re.compile(r"^(\[?\d+\]?\s+.+|[A-Z][A-Za-z'\-]+.*\(\d{4}[a-z]?\).+)$")
STOPWORDS = {
    "the", "and", "for", "with", "from", "that", "this", "into", "are", "was", "were", "you", "your",
    "have", "has", "had", "not", "but", "can", "will", "use", "using", "used", "into", "than", "per",
    "文档", "内容", "进行", "以及", "相关", "使用", "一个", "可以", "通过", "当前", "系统", "流程",
}


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "")).strip()


def infer_type(text: str, bold: bool, font_size: float, baseline: float) -> str:
    normalized = normalize_text(text)
    if not normalized:
        return "Text"
    if CAPTION_RE.match(normalized):
        return "Caption"
    if TABLE_RE.match(normalized):
        return "Table"
    if LIST_RE.match(normalized):
        return "List"
    ratio = (font_size / baseline) if baseline else 1.0
    if ratio >= 1.25 or (bold and ratio >= 1.1):
        return "Title"
    return "Text"


def bbox_from_box(box):
    return {
        "x1": float(box.get("x1", 0.0)),
        "y1": float(box.get("y1", 0.0)),
        "x2": float(box.get("x2", 0.0)),
        "y2": float(box.get("y2", 0.0)),
    }


def render_images(payload_pages):
    return [
        {
            "page_num": page["page_num"],
            "image_path": page.get("image_path"),
            "image_width": page.get("image_width"),
            "image_height": page.get("image_height"),
            "scale_factor": (page.get("image_width", 0) / page.get("page_width", 1)) if page.get("page_width") else 1.0,
        }
        for page in payload_pages
    ]


def ocr(payload_pages):
    pages = {}
    for page in payload_pages:
        page_num = page["page_num"]
        boxes = []
        for index, raw in enumerate(page.get("native_boxes", [])):
            boxes.append({
                "id": f"ocr-{page_num}-{index}",
                "page_num": page_num,
                "text": normalize_text(raw.get("text", "")),
                "bbox": bbox_from_box(raw),
                "font_size": float(raw.get("fontSize", 12.0)),
                "bold": bool(raw.get("bold", False)),
                "source": raw.get("source", "pdf_native"),
                "confidence": 1.0,
                "attributes": {},
            })
        pages[page_num] = boxes
    return pages


def layouts_rec(ocr_pages):
    all_sizes = [box["font_size"] for boxes in ocr_pages.values() for box in boxes if box["font_size"] > 0]
    baseline = sorted(all_sizes)[len(all_sizes) // 2] if all_sizes else 12.0
    layout_pages = {}
    for page_num, boxes in ocr_pages.items():
        layout_pages[page_num] = []
        for box in boxes:
            block_type = infer_type(box["text"], box["bold"], box["font_size"], baseline)
            block = dict(box)
            block["type"] = block_type
            block["attributes"] = dict(box.get("attributes", {}))
            block["attributes"]["fontSize"] = box["font_size"]
            block["attributes"]["isBold"] = box["bold"]
            layout_pages[page_num].append(block)
    return layout_pages, baseline


def split_table_cells(text: str):
    if "|" in text:
        cells = [part.strip() for part in text.split("|") if part.strip()]
        return cells
    return [part.strip() for part in re.split(r"\s{2,}", text) if part.strip()]


def table_transformer_job(layout_pages):
    for _, blocks in layout_pages.items():
        for block in blocks:
            if block["type"] != "Table":
                continue
            rows = [split_table_cells(line) for line in block["text"].splitlines() if split_table_cells(line)]
            if not rows:
                continue
            headers = rows[0]
            body_rows = rows[1:] if len(rows) > 1 else []
            markdown = []
            markdown.append("| " + " | ".join(headers) + " |")
            markdown.append("| " + " | ".join(["---"] * len(headers)) + " |")
            for row in body_rows:
                normalized = row + [""] * (len(headers) - len(row))
                markdown.append("| " + " | ".join(normalized[: len(headers)]) + " |")
            html = ["<table>", "<thead><tr>"]
            for cell in headers:
                html.append(f"<th>{cell}</th>")
            html.append("</tr></thead>")
            if body_rows:
                html.append("<tbody>")
                for row in body_rows:
                    normalized = row + [""] * (len(headers) - len(row))
                    html.append("<tr>")
                    for cell in normalized[: len(headers)]:
                        html.append(f"<td>{cell}</td>")
                    html.append("</tr>")
                html.append("</tbody>")
            html.append("</table>")
            block["attributes"]["tableMarkdown"] = "\n".join(markdown)
            block["attributes"]["tableHtml"] = "".join(html)
    return layout_pages


def merge_two_blocks(left, right):
    merged = dict(left)
    merged["text"] = normalize_text(left["text"] + " " + right["text"])
    merged["bbox"] = {
        "x1": min(left["bbox"]["x1"], right["bbox"]["x1"]),
        "y1": min(left["bbox"]["y1"], right["bbox"]["y1"]),
        "x2": max(left["bbox"]["x2"], right["bbox"]["x2"]),
        "y2": max(left["bbox"]["y2"], right["bbox"]["y2"]),
    }
    merged["font_size"] = max(left["font_size"], right["font_size"])
    merged["bold"] = left["bold"] or right["bold"]
    attrs = dict(left.get("attributes", {}))
    attrs.update(right.get("attributes", {}))
    merged["attributes"] = attrs
    return merged


def text_merge(layout_pages):
    merged_pages = {}
    for page_num, blocks in layout_pages.items():
        ordered = sorted(blocks, key=lambda b: (b["bbox"]["y1"], b["bbox"]["x1"]))
        merged = []
        for block in ordered:
            if not merged:
                merged.append(block)
                continue
            previous = merged[-1]
            same_type = previous["type"] == block["type"]
            y_gap = abs(block["bbox"]["y1"] - previous["bbox"]["y1"])
            x_gap = block["bbox"]["x1"] - previous["bbox"]["x2"]
            if same_type and y_gap <= max(previous["font_size"], block["font_size"]) * 0.7 and 0 <= x_gap <= max(18.0, previous["font_size"] * 1.5):
                merged[-1] = merge_two_blocks(previous, block)
            else:
                merged.append(block)
        merged_pages[page_num] = merged
    return merged_pages


def kmeans_1d(values, k, iterations=12):
    unique = sorted(values)
    if not unique:
        return []
    if k == 1:
        return [sum(unique) / len(unique)]
    centers = [unique[min(len(unique) - 1, round(i * (len(unique) - 1) / (k - 1)))] for i in range(k)]
    for _ in range(iterations):
        groups = [[] for _ in range(k)]
        for value in values:
            index = min(range(k), key=lambda idx: abs(value - centers[idx]))
            groups[index].append(value)
        next_centers = []
        for idx, group in enumerate(groups):
            next_centers.append(sum(group) / len(group) if group else centers[idx])
        if all(abs(next_centers[i] - centers[i]) < 1e-3 for i in range(k)):
            centers = next_centers
            break
        centers = next_centers
    return sorted(centers)


def assign_column(layout_pages, payload_pages):
    page_meta = {page["page_num"]: page for page in payload_pages}
    column_layouts = {}
    for page_num, blocks in layout_pages.items():
        width = float(page_meta[page_num].get("page_width", 0.0))
        x_centers = [((b["bbox"]["x1"] + b["bbox"]["x2"]) / 2.0) for b in blocks if b["type"] != "Figure"]
        best_k = 1
        best_centers = [width / 2.0] if width else [0.0]
        previous_sse = None
        for k in range(1, min(4, len(x_centers)) + 1):
            centers = kmeans_1d(x_centers, k)
            sse = sum(min((value - center) ** 2 for center in centers) for value in x_centers)
            if previous_sse is not None and previous_sse > 0:
                improvement = (previous_sse - sse) / previous_sse
                min_gap = min((centers[i + 1] - centers[i]) for i in range(len(centers) - 1)) if len(centers) > 1 else width
                if improvement >= 0.25 and min_gap >= width * 0.10:
                    best_k = k
                    best_centers = centers
            previous_sse = sse
        boundaries = [(best_centers[i] + best_centers[i + 1]) / 2.0 for i in range(len(best_centers) - 1)]
        column_layouts[page_num] = {"column_count": best_k, "centers": best_centers, "boundaries": boundaries}
        for block in blocks:
            center = (block["bbox"]["x1"] + block["bbox"]["x2"]) / 2.0
            column_index = 0
            for index, boundary in enumerate(boundaries):
                if center >= boundary:
                    column_index = index + 1
            block["column_index"] = column_index
            left_index = 0
            right_index = 0
            for index, boundary in enumerate(boundaries):
                if block["bbox"]["x1"] >= boundary:
                    left_index = index + 1
                if block["bbox"]["x2"] >= boundary:
                    right_index = index + 1
            block["attributes"]["spanColumns"] = left_index != right_index
    return layout_pages, column_layouts


def extract_table_figure(layout_pages, payload_pages):
    page_meta = {page["page_num"]: page for page in payload_pages}
    for page in payload_pages:
        page_num = page["page_num"]
        for idx, figure in enumerate(page.get("figures", [])):
            layout_pages.setdefault(page_num, []).append({
                "id": f"figure-{page_num}-{idx}",
                "page_num": page_num,
                "text": "[Figure]",
                "bbox": {
                    "x1": float(figure.get("x1", 0.0)),
                    "y1": float(figure.get("y1", 0.0)),
                    "x2": float(figure.get("x2", 0.0)),
                    "y2": float(figure.get("y2", 0.0)),
                },
                "font_size": 0.0,
                "bold": False,
                "source": "image_xobject",
                "confidence": 1.0,
                "type": "Figure",
                "column_index": 0,
                "attributes": {
                    "width": figure.get("width"),
                    "height": figure.get("height"),
                    "spanColumns": (figure.get("width", 0.0) >= page_meta[page_num].get("page_width", 0.0) * 0.6),
                },
            })
    for page_num, blocks in layout_pages.items():
        blocks.sort(key=lambda b: (b.get("column_index", 0), b["bbox"]["y1"], b["bbox"]["x1"]))
        for index, block in enumerate(blocks):
            if block["type"] != "Table":
                continue
            previous = blocks[index - 1] if index > 0 else None
            next_block = blocks[index + 1] if index + 1 < len(blocks) else None
            caption = None
            if previous and previous["type"] == "Caption" and normalize_text(previous["text"]).lower().startswith(("table", "表")):
                caption = previous["text"]
            elif next_block and next_block["type"] == "Caption" and normalize_text(next_block["text"]).lower().startswith(("table", "表")):
                caption = next_block["text"]
            if caption:
                block["attributes"]["tableCaption"] = caption
    return layout_pages


def build_candidate(block):
    return {
        "page_num": block["page_num"],
        "column_index": block.get("column_index", 0),
        "type": block["type"],
        "texts": [block["text"]],
        "bbox": dict(block["bbox"]),
        "font_size": block["font_size"],
        "bold": block["bold"],
        "attributes": dict(block.get("attributes", {})),
    }


def merge_candidate(current, nxt, feature_payload):
    merged = dict(current)
    merged["texts"] = current["texts"] + nxt["texts"]
    merged["bbox"] = {
        "x1": min(current["bbox"]["x1"], nxt["bbox"]["x1"]),
        "y1": min(current["bbox"]["y1"], nxt["bbox"]["y1"]),
        "x2": max(current["bbox"]["x2"], nxt["bbox"]["x2"]),
        "y2": max(current["bbox"]["y2"], nxt["bbox"]["y2"]),
    }
    attrs = dict(current["attributes"])
    attrs.update(nxt.get("attributes", {}))
    attrs["concatFeature"] = feature_payload
    merged["attributes"] = attrs
    return merged


def naive_vertical_merge(layout_pages):
    candidates = {}
    for page_num, blocks in layout_pages.items():
        ordered = sorted(blocks, key=lambda b: (b.get("column_index", 0), b["bbox"]["y1"], b["bbox"]["x1"]))
        page_candidates = []
        for block in ordered:
            if block["type"] in {"Figure", "Caption", "Table", "Title"}:
                page_candidates.append(build_candidate(block))
                continue
            if not page_candidates:
                page_candidates.append(build_candidate(block))
                continue
            previous = page_candidates[-1]
            gap = block["bbox"]["y1"] - previous["bbox"]["y2"]
            if previous["type"] == block["type"] and previous["column_index"] == block.get("column_index", 0) and gap <= max(previous["font_size"], block["font_size"]) * 1.6:
                page_candidates[-1] = merge_candidate(previous, build_candidate(block), {"phase": "naive_vertical_merge", "gap": gap})
            else:
                page_candidates.append(build_candidate(block))
        candidates[page_num] = page_candidates
    return candidates


def concat_downward(candidate_pages):
    merged_pages = {}
    for page_num, candidates in candidate_pages.items():
        merged = []
        for candidate in candidates:
            if not merged:
                merged.append(candidate)
                continue
            previous = merged[-1]
            prev_text = normalize_text(" ".join(previous["texts"]))
            next_text = normalize_text(" ".join(candidate["texts"]))
            y_gap = candidate["bbox"]["y1"] - previous["bbox"]["y2"]
            x_delta = abs(candidate["bbox"]["x1"] - previous["bbox"]["x1"])
            same_column = previous["column_index"] == candidate["column_index"]
            font_delta = abs(previous["font_size"] - candidate["font_size"])
            continuation = prev_text and prev_text[-1] not in "。！？.!?;；:："
            should_merge = (
                previous["type"] == candidate["type"] == "Text"
                and same_column
                and y_gap <= max(previous["font_size"], candidate["font_size"]) * 1.8
                and x_delta <= max(24.0, previous["font_size"] * 2.0)
                and font_delta <= 1.5
            )
            if continuation:
                should_merge = should_merge and True
            feature_payload = {
                "phase": "concat_downward",
                "yGap": y_gap,
                "xDelta": x_delta,
                "sameColumn": same_column,
                "fontDelta": font_delta,
                "continuation": continuation,
            }
            if should_merge:
                merged[-1] = merge_candidate(previous, candidate, feature_payload)
            else:
                candidate["attributes"]["concatFeature"] = feature_payload
                merged.append(candidate)
        merged_pages[page_num] = merged
    return merged_pages


def markdown_heading(level, text):
    return "#" * max(1, min(level, 6)) + " " + text


def resolve_heading_level(text, font_size, baseline, strategy):
    normalized = normalize_text(text)
    if strategy == "manual" and MANUAL_HEADING_RE.match(normalized):
        if normalized.startswith("第"):
            return 1
        return min(4, normalized.count(".") + 1)
    ratio = (font_size / baseline) if baseline else 1.0
    if ratio >= 1.6:
        return 1
    if ratio >= 1.4:
        return 2
    return 3


def extract_keywords(text, limit=6):
    english_tokens = re.findall(r"[A-Za-z][A-Za-z0-9_\-]{2,}", text or "")
    chinese_tokens = re.findall(r"[\u4e00-\u9fff]{2,}", text or "")
    counts = {}
    for token in english_tokens + chinese_tokens:
        normalized = token.lower()
        if normalized in STOPWORDS or normalized.isdigit():
            continue
        counts[normalized] = counts.get(normalized, 0) + 1
    return [token for token, _ in sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:limit]]


def final_reading_order_merge(candidate_pages, payload_pages, baseline, strategy):
    page_meta = {page["page_num"]: page for page in payload_pages}
    pages_out = []
    segments = []
    heading_stack = []
    current_section_role = None
    reading_order = 0

    for page in payload_pages:
        page_num = page["page_num"]
        candidates = sorted(candidate_pages.get(page_num, []), key=lambda c: (c["column_index"], c["bbox"]["y1"], c["bbox"]["x1"]))
        page_blocks = []
        for index, candidate in enumerate(candidates):
            content = normalize_text(" ".join(candidate["texts"]))
            if not content:
                continue
            block_type = candidate["type"]
            segment_type = {
                "Title": "heading",
                "Text": "paragraph",
                "List": "list",
                "Table": "table",
                "Caption": "caption",
                "Figure": "figure",
            }.get(block_type, "paragraph")
            metadata = {
                "docType": "pdf",
                "pageNum": page_num,
                "columnIndex": candidate["column_index"],
                "bbox": candidate["bbox"],
                "segmentType": segment_type,
                "layoutBlockType": block_type,
                "keywords": extract_keywords(content),
                "readingOrder": reading_order,
            }
            metadata.update(candidate.get("attributes", {}))
            heading_path = " > ".join(heading_stack) if heading_stack else None
            segment_content = content

            is_heading = block_type == "Title" or (strategy == "manual" and segment_type == "paragraph" and MANUAL_HEADING_RE.match(content))
            if is_heading:
                segment_type = "heading"
                level = resolve_heading_level(content, candidate["font_size"], baseline, strategy)
                while len(heading_stack) >= level:
                    heading_stack.pop()
                heading_stack.append(content)
                heading_path = " > ".join(heading_stack)
                segment_content = markdown_heading(level, content)
                metadata["headingLevel"] = level
                metadata["outlineCandidate"] = True
                if block_type != "Title":
                    metadata["promotedHeading"] = True
                if ABSTRACT_RE.match(content):
                    metadata["sectionRole"] = "abstract_heading"
                    current_section_role = "abstract"
                elif REFERENCE_HEADING_RE.match(content):
                    metadata["sectionRole"] = "references_heading"
                    current_section_role = "references"
                else:
                    current_section_role = None
            elif current_section_role == "abstract" and segment_type == "paragraph":
                metadata["sectionRole"] = "abstract"
            elif current_section_role == "references" and REFERENCE_ENTRY_RE.match(content):
                metadata["sectionRole"] = "reference_entry"
            elif segment_type == "caption":
                lowered = content.lower()
                if lowered.startswith("table") or lowered.startswith("表"):
                    metadata["sectionRole"] = "table_caption"
                elif lowered.startswith("figure") or lowered.startswith("图"):
                    metadata["sectionRole"] = "figure_caption"

            if strategy == "table" and segment_type == "caption":
                reading_order += 1
                continue

            parent_section_id = None
            section_id = f"sec-{page_num}-{reading_order}"
            if segment_type == "heading":
                metadata["sectionId"] = section_id
                metadata["sectionDepth"] = metadata.get("headingLevel", 1)
            elif heading_stack:
                parent_section_id = f"sec-path-{abs(hash(' > '.join(heading_stack)))}"
                metadata["parentSectionId"] = parent_section_id
                metadata["sectionDepth"] = len(heading_stack) + 1

            segments.append({
                "id": f"pdf-seg-{reading_order}",
                "type": segment_type,
                "content": segment_content,
                "headingPath": heading_path,
                "startOrder": reading_order,
                "endOrder": reading_order,
                "metadata": metadata,
            })
            page_blocks.append({
                "id": f"pdf-block-{page_num}-{index}",
                "type": block_type,
                "bbox": candidate["bbox"],
                "text": content,
                "html": candidate.get("attributes", {}).get("tableHtml"),
                "pageNum": page_num,
                "columnIndex": candidate["column_index"],
                "confidence": 1.0,
                "attributes": dict(metadata),
            })
            reading_order += 1

        pages_out.append({
            "pageNum": page_num,
            "columnCount": max([c.get("column_index", 0) for c in candidates], default=0) + 1,
            "blocks": page_blocks,
            "metadata": {
                "width": page_meta[page_num].get("page_width"),
                "height": page_meta[page_num].get("page_height"),
            },
        })
    return pages_out, segments


def run_pipeline(payload, strategy):
    payload_pages = payload.get("pages", [])
    step_images = render_images(payload_pages)
    step_ocr = ocr(payload_pages)
    step_layouts, baseline = layouts_rec(step_ocr)
    step_tables = table_transformer_job(step_layouts)
    step_merged = text_merge(step_tables)
    step_columns, column_layouts = assign_column(step_merged, payload_pages)
    step_extracted = extract_table_figure(step_columns, payload_pages)
    step_candidates = naive_vertical_merge(step_extracted)
    step_concat = concat_downward(step_candidates)
    pages_out, segments = final_reading_order_merge(step_concat, payload_pages, baseline, strategy)
    document_metadata = {
        "docType": "pdf",
        "parserEngine": "python_module",
        "parseStrategy": strategy,
        "pageCount": len(payload_pages),
        "segmentCount": len(segments),
        "renderDpi": payload.get("render_dpi"),
        "pipelineSteps": [
            "images", "ocr", "_layouts_rec", "_table_transformer_job", "_text_merge",
            "_assign_column", "_extract_table_figure", "_naive_vertical_merge",
            "_concat_downward", "_final_reading_order_merge",
        ],
        "detectedColumns": {str(page_num): layout["column_count"] for page_num, layout in column_layouts.items()},
        "imagesRendered": len(step_images),
    }
    return {"pages": pages_out, "segments": segments, "documentMetadata": document_metadata}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-json", required=True)
    parser.add_argument("--output-json", required=True)
    parser.add_argument("--strategy", default="naive")
    args = parser.parse_args()

    payload = json.loads(Path(args.input_json).read_text(encoding="utf-8"))
    result = run_pipeline(payload, args.strategy.strip().lower())
    Path(args.output_json).write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
