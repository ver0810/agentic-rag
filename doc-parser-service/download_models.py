import os
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

from pathlib import Path
from loguru import logger
from huggingface_hub import snapshot_download


def download_deepdoc_models(model_dir: str | Path, force: bool = False) -> Path:
    """Download deepdoc models using HF mirror."""
    model_dir = Path(model_dir)
    model_dir.mkdir(parents=True, exist_ok=True)

    target = model_dir / "deepdoc"
    if target.exists() and not force:
        existing = list(target.glob("*.onnx"))
        if existing:
            logger.info(f"Models already exist at {target}: {[f.name for f in existing]}")
            return target

    logger.info("Downloading deepdoc models from HuggingFace mirror...")

    try:
        snapshot_download(
            repo_id="InfiniFlow/deepdoc",
            local_dir=str(target),
            local_dir_use_symlinks=False,
            allow_patterns=["*.onnx", "*.res", "*.json", "*.txt"],
        )
        logger.info(f"Models downloaded to {target}")
    except Exception as e:
        logger.error(f"Failed to download models: {e}")
        raise

    return target


if __name__ == "__main__":
    download_deepdoc_models("models")
