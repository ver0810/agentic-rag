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
- `scripts/run-rag-eval.ps1`

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
