#!/usr/bin/env python3
"""一键运行RAGAS评测脚本"""

import json
from pathlib import Path
from urllib.request import Request, urlopen

BASE_URL = "http://localhost:8080"
KB_ID = "2055173188384915459"
USERNAME = "admin"
PASSWORD = "admin"
DATASET_PATH = Path(__file__).parent.parent.parent / "artifacts" / "ragas-eval" / "amnesty_qa-20260511-140111.json"


def get_token() -> str:
    req = Request(
        f"{BASE_URL}/user/login",
        data=json.dumps({"username": USERNAME, "password": PASSWORD}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(req) as resp:
        return json.loads(resp.read())["accessToken"]


def run_eval(token: str) -> dict:
    samples = json.loads(DATASET_PATH.read_text(encoding="utf-8"))
    # 从评测集中提取samples字段，如果没有则使用整个文件
    if isinstance(samples, dict) and "cases" in samples:
        eval_samples = [
            {
                "id": c.get("id", ""),
                "question": c.get("query", ""),
                "groundTruth": c.get("expectedAnswer", ""),
            }
            for c in samples["cases"]
        ]
    else:
        eval_samples = samples

    req = Request(
        f"{BASE_URL}/api/eval/ragas/run",
        data=json.dumps({"kbId": KB_ID, "samples": eval_samples}).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
        method="POST",
    )
    with urlopen(req) as resp:
        return json.loads(resp.read())


def main():
    print(f"知识库ID: {KB_ID}")
    print(f"评测集: {DATASET_PATH.name}")
    print("获取Token...")
    token = get_token()
    print("运行评测...")
    report = run_eval(token)

    out_dir = Path(__file__).parent.parent.parent / "artifacts" / "ragas-eval"
    out_file = out_dir / f"report-{report['evalRunId']}.json"
    out_file.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("=" * 50)
    print(f"RunId: {report['evalRunId']}")
    print(f"样本数: {report['totalSamples']}")
    print(f"综合分: {report.get('overallScore', '-')}")
    print(f"忠实度: {report.get('avgFaithfulness', '-')}")
    print(f"相关性: {report.get('avgAnswerRelevancy', '-')}")
    print(f"精确度: {report.get('avgContextPrecision', '-')}")
    print(f"召回率: {report.get('avgContextRecall', '-')}")
    print(f"正确性: {report.get('avgAnswerCorrectness', '-')}")
    print("=" * 50)
    print(f"报告已保存: {out_file}")


if __name__ == "__main__":
    main()
