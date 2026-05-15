from __future__ import annotations

import argparse
import json
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin
from urllib.request import Request, urlopen


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare two project RAG evaluation runs.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Backend base URL.")
    parser.add_argument("--base-run-id", required=True, help="Baseline run ID.")
    parser.add_argument("--target-run-id", required=True, help="Target run ID.")
    parser.add_argument("--username", help="Login username.")
    parser.add_argument("--password", help="Login password.")
    parser.add_argument("--access-token", help="Existing bearer token.")
    return parser.parse_args()


def request_json(request: Request) -> dict:
    try:
        with urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise SystemExit(f"Request failed: {exc.reason}") from exc


def post_json(url: str, payload: dict) -> dict:
    request = Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return request_json(request)


def get_access_token(base_url: str, username: str | None, password: str | None, access_token: str | None) -> str:
    if access_token:
        return access_token
    if not username or not password:
        raise SystemExit("Provide --access-token or both --username and --password.")
    login_response = post_json(
        urljoin(base_url.rstrip("/") + "/", "user/login"),
        {"username": username, "password": password},
    )
    token = login_response.get("accessToken")
    if not token:
        raise SystemExit("Login succeeded but accessToken was empty.")
    return token


def main() -> None:
    args = parse_args()
    base_url = args.base_url.rstrip("/")
    token = get_access_token(base_url, args.username, args.password, args.access_token)
    query = urlencode({"baseRunId": args.base_run_id, "targetRunId": args.target_run_id})
    request = Request(
        f"{base_url}/api/rag/evals/compare?{query}",
        headers={"Authorization": f"Bearer {token}"},
        method="GET",
    )
    report = request_json(request)

    print(f"BaseRun: {report['baseRun']['runId']}")
    print(f"TargetRun: {report['targetRun']['runId']}")
    print(f"PassRateDelta: {report['delta']['passRateDelta']}%")
    print(f"AnswerAccuracyDelta: {report['delta']['answerAccuracyDelta']}%")
    print(f"CitationHitRateDelta: {report['delta']['citationHitRateDelta']}%")
    print(f"RefusalAccuracyDelta: {report['delta']['refusalAccuracyDelta']}%")
    print("ChangedCases:")
    for diff in report.get("caseDiffs", []):
        if diff.get("change") == "unchanged":
            continue
        print(
            f"- {diff.get('caseId')}: {diff.get('change')} | "
            f"base={diff.get('baseFailureReason')} | target={diff.get('targetFailureReason')} | "
            f"trace={diff.get('targetTraceId')}"
        )


if __name__ == "__main__":
    main()
