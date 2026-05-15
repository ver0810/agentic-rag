# Eval Scripts

## DuReader

项目现在支持把官方 DuReader 原始数据文件转换为当前规则评测接口可直接使用的格式。

支持输入：
- 单个 `jsonl` / `json` 文件
- 包含多个原始文件的目录

支持输出：
- `rag-eval`

### 转为规则评测集

```bash
python scripts/eval/convert_dureader.py \
  --input path/to/dureader/devset \
  --output bootstrap/src/main/resources/rag-eval/dureader-dev.json \
  --format rag-eval \
  --dataset-name dureader-dev \
  --limit 200 \
  --top-k 5
```

生成后的文件可直接配合：
- `POST /api/rag/evals/run`
- `python scripts/eval/run_rag_eval.py`

### 规范化真实评测集

如果你已经有一份真实评测集，但里面的 `expectedDocNames` 还没有和知识库中的实际文档名对齐，可以先做一次规范化：

```bash
python scripts/eval/build_real_eval_dataset.py \
  --input artifacts/ragas-eval/amnesty_qa-20260511-140403.json \
  --output bootstrap/src/main/resources/rag-eval/amnesty_qa_real.json \
  --doc-name amnesty_qa-corpus-20260511-140353.txt \
  --dataset-name amnesty_qa_real \
  --limit 20
```

### 运行评测

```bash
python scripts/eval/run_rag_eval.py \
  --base-url http://localhost:8080 \
  --dataset sample-template \
  --kb-id your-kb-id \
  --username admin \
  --password 123456
```

### 对比两次评测

```bash
python scripts/eval/compare_rag_eval.py \
  --base-url http://localhost:8080 \
  --base-run-id run-a \
  --target-run-id run-b \
  --username admin \
  --password 123456
```

### 导出可入库语料

如果你要先把 DuReader 文档导入知识库，再运行评测，可以先导出 TXT 语料：

```bash
python scripts/eval/export_dureader_corpus.py \
  --input path/to/dureader/devset \
  --output-dir artifacts/dureader-corpus \
  --limit 200 \
  --max-docs-per-sample 3
```

输出内容：
- 多个 `*.txt` 文档
- 一个 `manifest.json`

这些 `txt` 文件可以直接通过知识库上传接口导入项目，再触发解析和向量化。

### 说明

- 当前转换逻辑默认取每条样本的第一个非空答案作为 `groundTruth`
- `groundTruthContexts` 会优先保留 `is_selected=true` 的文档内容
- DuReader 原始样本可能存在长答案、弱标注或多答案情况，建议先从 `dev` 集抽样开始
