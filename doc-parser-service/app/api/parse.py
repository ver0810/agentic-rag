import shutil
import tempfile
from pathlib import Path

from fastapi import APIRouter, File, Form, UploadFile, HTTPException
from loguru import logger

from app.config import settings
from app.core.parser_factory import parser_factory
from app.core.task_manager import task_manager
from app.models.response import ParseResult, AsyncTaskResponse, TaskStatusResponse

router = APIRouter(prefix="/api/v1", tags=["parse"])


@router.post("/parse", response_model=ParseResult)
async def parse_document(
    file: UploadFile = File(...),
    strategy: str = Form(settings.default_strategy),
    language: str = Form(settings.default_language),
):
    if file.filename is None:
        raise HTTPException(status_code=400, detail="Filename is required")

    suffix = Path(file.filename).suffix
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = Path(tmp.name)

    try:
        result = parser_factory.parse(tmp_path, strategy, language)
        return result
    finally:
        tmp_path.unlink(missing_ok=True)


@router.post("/parse/async", response_model=AsyncTaskResponse)
async def parse_document_async(
    file: UploadFile = File(...),
    strategy: str = Form(settings.default_strategy),
    language: str = Form(settings.default_language),
):
    if file.filename is None:
        raise HTTPException(status_code=400, detail="Filename is required")

    suffix = Path(file.filename).suffix
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = Path(tmp.name)

    task_id = task_manager.create_task()
    # fire and forget
    import asyncio
    asyncio.create_task(task_manager.run_parse(task_id, tmp_path, strategy, language))

    return AsyncTaskResponse(task_id=task_id, status="pending")


@router.get("/status/{task_id}", response_model=TaskStatusResponse)
async def get_task_status(task_id: str):
    info = task_manager.get_task(task_id)
    if info is None:
        raise HTTPException(status_code=404, detail="Task not found")
    return TaskStatusResponse(
        task_id=info.task_id,
        status=info.status.value,
        progress=info.progress,
        result=info.result,
        error_message=info.error_message,
    )
