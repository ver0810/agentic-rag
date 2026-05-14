from fastapi import APIRouter
from app.models.response import HealthResponse
from app.config import settings
from app.utils.model_downloader import check_models

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health_check():
    model_status = check_models(settings.model_dir)
    models_loaded = all(model_status.values())
    return HealthResponse(
        status="healthy",
        version=settings.app_version,
        models_loaded=models_loaded,
    )
