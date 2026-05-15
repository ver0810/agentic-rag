"""
DuReader 语料导出脚本

将官方 DuReader 原始数据中的候选文档导出为可直接入库的 TXT 文件，
同时生成 manifest，便于后续将评测样本与导入文档对应起来。
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from convert_dureader import iter_records, iter_source_files, first_non_empty_answer


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export DuReader source documents into TXT corpus files.")
    parser.add_argument(
        "--input",
        required=True,
        help="Path to a DuReader source file or a directory containing *.json / *.jsonl files.",
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Directory to write exported TXT files and manifest.json.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=200,
        help="Maximum number of usable QA samples to export.",
    )
    parser.add_argument(
        "--max-docs-per-sample",
        type=int,
        default=3,
        help="Maximum number of documents exported per sample.",
    )
    parser.add_argument(
        "--max-paragraph-chars",
        type=int,
        default=3000,
        help="Maximum characters kept per exported document.",
    )
    return parser.parse_args()


def normalize_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def sanitize_filename(value: str) -> str:
    sanitized = re.sub(r"[^0-9A-Za-z_\-\u4e00-\u9fff]+", "_", value).strip("_")
    return sanitized or "sample"


def select_documents(record: dict, max_docs_per_sample: int, max_paragraph_chars: int) -> list[dict]:
    documents = record.get("documents")
    if not isinstance(documents, list):
        return []

    selected = [doc for doc in documents if isinstance(doc, dict) and doc.get("is_selected", False)]
    candidates = selected if selected else [doc for doc in documents if isinstance(doc, dict)]

    exported: list[dict] = []
    for index, doc in enumerate(candidates, start=1):
        paragraphs = doc.get("paragraphs")
        if not isinstance(paragraphs, list) or not paragraphs:
            continue

        title = normalize_whitespace(str(doc.get("title") or ""))
        content_parts: list[str] = []
        total_chars = 0
        for paragraph in paragraphs:
            if not isinstance(paragraph, str):
                continue
            normalized = normalize_whitespace(paragraph)
            if not normalized:
                continue
            content_parts.append(normalized)
            total_chars += len(normalized)
            if total_chars >= max_paragraph_chars:
                break

        if not content_parts:
            continue

        exported.append(
            {
                "doc_index": index,
                "title": title,
                "content": "\n\n".join(content_parts)[:max_paragraph_chars],
                "is_selected": bool(doc.get("is_selected", False)),
            }
        )
        if len(exported) >= max_docs_per_sample:
            break

    return exported


def render_document(sample_id: str, question: str, answer: str, document: dict) -> str:
    header_lines = [
        f"Sample ID: {sample_id}",
        f"Question: {question}",
        f"Reference Answer: {answer}",
        f"Document Index: {document['doc_index']}",
        f"Selected By Dataset: {'yes' if document['is_selected'] else 'no'}",
    ]
    if document["title"]:
        header_lines.append(f"Title: {document['title']}")

    return "\n".join(header_lines) + "\n\nContent:\n" + document["content"] + "\n"


def main() -> None:
    args = parse_args()
    source = Path(args.input)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    manifest: list[dict] = []
    exported_samples = 0

    for file_path in iter_source_files(source):
        for record in iter_records(file_path):
            question = record.get("question")
            if not isinstance(question, str) or not question.strip():
                continue

            answer = first_non_empty_answer(record)
            if not answer:
                continue

            sample_id = str(record.get("question_id") or record.get("id") or record.get("questionId") or "").strip()
            if not sample_id:
                continue

            documents = select_documents(record, args.max_docs_per_sample, args.max_paragraph_chars)
            if not documents:
                continue

            question_text = normalize_whitespace(question)
            sample_slug = sanitize_filename(sample_id)
            exported_files: list[str] = []

            for doc in documents:
                file_name = f"{sample_slug}-doc{doc['doc_index']}.txt"
                file_path_out = output_dir / file_name
                file_path_out.write_text(
                    render_document(sample_id, question_text, answer, doc),
                    encoding="utf-8",
                )
                exported_files.append(file_name)

            manifest.append(
                {
                    "sampleId": sample_id,
                    "question": question_text,
                    "groundTruth": answer,
                    "sourceFile": file_path.name,
                    "exportedDocs": exported_files,
                }
            )

            exported_samples += 1
            if exported_samples >= args.limit:
                break
        if exported_samples >= args.limit:
            break

    if not manifest:
        raise SystemExit("No usable DuReader samples were exported.")

    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(
        json.dumps(
            {
                "dataset": "dureader-corpus",
                "totalSamples": len(manifest),
                "documentsPerSample": args.max_docs_per_sample,
                "items": manifest,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"Exported {len(manifest)} samples to {output_dir}")
    print(f"Manifest: {manifest_path}")


if __name__ == "__main__":
    main()
