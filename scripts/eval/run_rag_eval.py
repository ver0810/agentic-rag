from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run RAGAS evaluation via HTTP API.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Backend base URL.")
    parser.add_argument("--kb-id", required=True, help="Knowledge base ID.")
    parser.add_argument("--samples", required=True, help="JSON file with samples or inline JSON string.")
    parser.add_argument("--username", help="Login username.")
    parser.add_argument("--password", help="Login password.")
    parser.add_argument("--access-token", help="Existing bearer token.")
    parser.add_argument("--out-file", help="Write full JSON report to this path.")
    return parser.parse_args()


def post_json(url: str, payload: dict, headers: dict[str, str] | None = None) -> dict:
    body = json.dumps(payload).encode("utf-8")
    request_headers = {"Content-Type": "application/json", **(headers or {})}
    request = Request(url, data=body, headers=request_headers, method="POST")
    return request_json(request)


def request_json(request: Request) -> dict:
    try:
        with urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise SystemExit(f"Request failed: {exc.reason}") from exc


def get_access_token(base_url: str, username: str | None, password: str | None, access_token: str | None) -> str:
    if access_token:
        return access_token
    if not username or not password:
        raise SystemExit("Provide --access-token or both --username and --password.")

    login_url = urljoin(normalize_base_url(base_url), "/user/login")
    login_response = post_json(login_url, {"username": username, "password": password})
    token = login_response.get("accessToken")
    if not token:
        raise SystemExit("Login succeeded but accessToken was empty.")
    return token


def normalize_base_url(base_url: str) -> str:
    return base_url.rstrip("/")


def default_output_path() -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path("artifacts") / "ragas-eval"
    out_dir.mkdir(parents=True, exist_ok=True)
    return out_dir / f"ragas-{timestamp}.json"


def load_samples(samples_arg: str) -> list[dict]:
    path = Path(samples_arg)
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    else:
        data = json.loads(samples_arg)

    if isinstance(data, list):
        return data
    if isinstance(data, dict) and "samples" in data:
        return data["samples"]
    raise SystemExit("Samples must be a list or contain a 'samples' key.")


def main() -> None:
    args = parse_args()
    base_url = normalize_base_url(args.base_url)
    token = get_access_token(base_url, args.username, args.password, args.access_token)
    samples = load_samples(args.samples)

    report = post_json(
        f"{base_url}/api/eval/ragas/run",
        {
            "kbId": args.kb_id,
            "samples": samples,
        },
        headers={"Authorization": f"Bearer {token}"},
    )

    out_file = Path(args.out_file) if args.out_file else default_output_path()
    out_file.parent.mkdir(parents=True, exist_ok=True)
    out_file.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"RunId: {report.get('evalRunId')}")
    print(f"KB: {report.get('kbId')}")
    print(f"Total Samples: {report.get('totalSamples')}")
    print(f"Overall Score: {report.get('overallScore')}")
    print(f"Avg Faithfulness: {report.get('avgFaithfulness')}")
    print(f"Avg Relevancy: {report.get('avgAnswerRelevancy')}")
    print(f"Avg Precision: {report.get('avgContextPrecision')}")
    print(f"Avg Recall: {report.get('avgContextRecall')}")
    print(f"Avg Correctness: {report.get('avgAnswerCorrectness')}")
    print(f"Report: {out_file}")


if __name__ == "__main__":
    main()
