from contextlib import asynccontextmanager
from fastapi import FastAPI
from loguru import logger

from app.config import settings
from app.api import parse, health
from app.utils.model_downloader import check_models


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Document Parser Service starting")
    settings.upload_dir.mkdir(parents=True, exist_ok=True)
    settings.temp_dir.mkdir(parents=True, exist_ok=True)
    settings.model_dir.mkdir(parents=True, exist_ok=True)

    # Check models
    model_status = check_models(settings.model_dir)
    loaded_count = sum(1 for v in model_status.values() if v)
    total_count = len(model_status)
    logger.info(f"Models status: {loaded_count}/{total_count} available")

    if settings.auto_download_models and loaded_count < total_count:
        logger.info("Auto-downloading missing models...")
        try:
            from app.utils.model_downloader import download_deepdoc_models
            download_deepdoc_models(settings.model_dir)
        except Exception as e:
            logger.error(f"Failed to download models: {e}")

    yield
    logger.info("Document Parser Service shutting down")


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    lifespan=lifespan,
)

app.include_router(health.router)
app.include_router(parse.router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=settings.debug)
