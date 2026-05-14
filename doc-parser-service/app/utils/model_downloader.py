from pathlib import Path
from loguru import logger


def download_deepdoc_models(model_dir: str | Path, force: bool = False) -> Path:
    model_dir = Path(model_dir)
    model_dir.mkdir(parents=True, exist_ok=True)

    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        logger.error("huggingface_hub not installed, run: pip install huggingface_hub")
        raise

    target = model_dir / "deepdoc"
    if target.exists() and not force:
        logger.info(f"Models already exist at {target}, skipping download")
        return target

    logger.info("Downloading deepdoc models from HuggingFace...")

    snapshot_download(
        repo_id="InfiniFlow/deepdoc",
        local_dir=str(target),
        local_dir_use_symlinks=False,
    )

    logger.info(f"Models downloaded to {target}")
    return target


def check_models(model_dir: str | Path) -> dict[str, bool]:
    model_dir = Path(model_dir)
    deepdoc_dir = model_dir / "deepdoc"

    required = {
        "det.onnx": deepdoc_dir / "det.onnx",
        "rec.onnx": deepdoc_dir / "rec.onnx",
        "layout.onnx": deepdoc_dir / "layout.onnx",
        "tsr.onnx": deepdoc_dir / "tsr.onnx",
        "ocr.res": deepdoc_dir / "ocr.res",
    }

    status = {}
    for name, path in required.items():
        status[name] = path.exists()
        if not path.exists():
            logger.warning(f"Model file missing: {path}")

    return status
