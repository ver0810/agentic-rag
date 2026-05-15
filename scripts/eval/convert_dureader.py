"""
DuReader 标准数据集转换脚本

支持将官方原始 DuReader 数据文件（JSON/JSONL）转换为当前项目可直接使用的规则评测格式。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert official DuReader files into project eval datasets.")
    parser.add_argument(
        "--input",
        required=True,
        help="Path to a DuReader source file or a directory containing *.json / *.jsonl files.",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Output JSON file path.",
    )
    parser.add_argument(
        "--format",
        choices=["rag-eval"],
        default="rag-eval",
        help="Target dataset format.",
    )
    parser.add_argument(
        "--dataset-name",
        default="dureader-standard",
        help="Dataset name in output metadata.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=200,
        help="Maximum number of usable samples to export.",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=5,
        help="Default topK for rag-eval output.",
    )
    parser.add_argument(
        "--max-contexts",
        type=int,
        default=3,
        help="Maximum selected documents/paragraph groups to keep per sample.",
    )
    parser.add_argument(
        "--max-context-chars",
        type=int,
        default=1200,
        help="Maximum characters kept for each context block.",
    )
    return parser.parse_args()


def iter_source_files(source: Path) -> Iterable[Path]:
    if source.is_file():
        yield source
        return

    for pattern in ("*.jsonl", "*.json"):
        for path in sorted(source.rglob(pattern)):
            yield path


def iter_records(path: Path) -> Iterable[dict]:
    if path.suffix.lower() == ".jsonl":
        with path.open("r", encoding="utf-8") as handle:
            for line in handle:
                text = line.strip()
                if text:
                    yield json.loads(text)
        return

    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)

    if isinstance(payload, list):
        for item in payload:
            if isinstance(item, dict):
                yield item
        return

    if isinstance(payload, dict):
        if isinstance(payload.get("data"), list):
            for item in payload["data"]:
                if isinstance(item, dict):
                    yield item
            return
        yield payload


def first_non_empty_answer(record: dict) -> str | None:
    answers = record.get("answers")
    if not isinstance(answers, list):
        return None

    for answer in answers:
        if isinstance(answer, str):
            normalized = " ".join(answer.split()).strip()
            if normalized:
                return normalized
    return None


def extract_contexts(record: dict, max_contexts: int, max_context_chars: int) -> list[str]:
    documents = record.get("documents")
    if not isinstance(documents, list):
        return []

    contexts: list[str] = []
    for document in documents:
        if not isinstance(document, dict):
            continue
        if not document.get("is_selected", False) and contexts:
            continue

        paragraphs = document.get("paragraphs")
        if not isinstance(paragraphs, list) or not paragraphs:
            continue

        text_parts: list[str] = []
        title = document.get("title")
        if isinstance(title, str) and title.strip():
            text_parts.append(title.strip())

        for paragraph in paragraphs:
            if not isinstance(paragraph, str):
                continue
            normalized = " ".join(paragraph.split()).strip()
            if normalized:
                text_parts.append(normalized)
            if sum(len(part) for part in text_parts) >= max_context_chars:
                break

        if not text_parts:
            continue

        block = "\n".join(text_parts)
        contexts.append(block[:max_context_chars])
        if len(contexts) >= max_contexts:
            break

    return contexts


def to_eval_sample(record: dict, source_name: str, max_contexts: int, max_context_chars: int) -> dict | None:
    question = record.get("question")
    if not isinstance(question, str) or not question.strip():
        return None

    answer = first_non_empty_answer(record)
    if not answer:
        return None

    sample_id = str(record.get("question_id") or record.get("id") or record.get("questionId") or "").strip()
    if not sample_id:
        return None

    contexts = extract_contexts(record, max_contexts, max_context_chars)
    return {
        "id": sample_id,
        "question": question.strip(),
        "groundTruth": answer,
        "groundTruthContexts": contexts,
        "metadata": {
            "questionType": record.get("question_type"),
            "sourceFile": source_name,
        },
    }


def to_rule_eval_case(sample: dict, top_k: int) -> dict:
    return {
        "id": sample["id"],
        "kbId": "",
        "query": sample["question"],
        "topK": top_k,
        "expectedAnswerContains": [sample["groundTruth"]],
        "expectedDocNames": [],
        "shouldRefuse": False,
        "metadata": sample.get("metadata", {}),
    }


def convert_records(args: argparse.Namespace) -> list[dict]:
    source = Path(args.input)
    samples: list[dict] = []

    for file_path in iter_source_files(source):
        for record in iter_records(file_path):
            sample = to_eval_sample(record, file_path.name, args.max_contexts, args.max_context_chars)
            if not sample:
                continue
            samples.append(sample)
            if len(samples) >= args.limit:
                return samples

    return samples


def build_output(samples: list[dict], args: argparse.Namespace) -> dict:
    return {
        "name": args.dataset_name,
        "description": "Converted from official DuReader source files.",
        "cases": [to_rule_eval_case(sample, args.top_k) for sample in samples],
    }


def main() -> None:
    args = parse_args()
    samples = convert_records(args)
    if not samples:
        raise SystemExit("No usable DuReader samples were found in the provided input.")

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = build_output(samples, args)

    with output_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)

    print(f"Converted {len(samples)} samples.")
    print(f"Output: {output_path}")
    print(f"Format: {args.format}")


if __name__ == "__main__":
    main()
