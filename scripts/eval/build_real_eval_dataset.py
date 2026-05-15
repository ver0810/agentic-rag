from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Normalize a real evaluation dataset so it matches the uploaded knowledge-base document names."
    )
    parser.add_argument("--input", required=True, help="Source dataset JSON path.")
    parser.add_argument("--output", required=True, help="Output dataset JSON path.")
    parser.add_argument(
        "--doc-name",
        required=True,
        help="Document name that will appear in the knowledge base, used for expectedDocNames.",
    )
    parser.add_argument(
        "--dataset-name",
        help="Override dataset name in output. Defaults to source name.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        help="Keep only the first N cases for a quick real-world smoke evaluation.",
    )
    return parser.parse_args()


def summarize_expected_answer(text: str) -> list[str]:
    normalized = " ".join((text or "").split())
    if not normalized:
        return []
    if len(normalized) <= 180:
        return [normalized]

    parts = [part.strip() for part in normalized.replace("?", ".").split(".") if part.strip()]
    if not parts:
        return [normalized[:180]]
    return parts[:3]


def main() -> None:
    args = parse_args()
    input_path = Path(args.input)
    output_path = Path(args.output)

    payload = json.loads(input_path.read_text(encoding="utf-8"))
    cases = payload.get("cases", [])
    if args.limit is not None:
        cases = cases[: args.limit]

    normalized_cases = []
    for case in cases:
        expected_answer = case.get("expectedAnswer", "")
        normalized_cases.append(
            {
                "id": case.get("id"),
                "kbId": "",
                "query": case.get("query"),
                "topK": case.get("topK", 5),
                "expectedAnswerContains": summarize_expected_answer(expected_answer),
                "expectedDocNames": [args.doc_name],
                "shouldRefuse": bool(case.get("shouldRefuse", False)),
            }
        )

    result = {
        "name": args.dataset_name or payload.get("name") or input_path.stem,
        "description": (
            payload.get("description")
            or "Normalized real evaluation dataset aligned with uploaded knowledge-base documents."
        ),
        "cases": normalized_cases,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Wrote {len(normalized_cases)} cases to {output_path}")
    print(f"Expected doc name: {args.doc_name}")


if __name__ == "__main__":
    main()
