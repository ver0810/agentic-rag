import asyncio
import uuid
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path

from app.core.parser_factory import parser_factory
from app.models.response import ParseResult


class TaskStatus(str, Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass
class TaskInfo:
    task_id: str
    status: TaskStatus = TaskStatus.PENDING
    progress: float = 0.0
    result: ParseResult | None = None
    error_message: str | None = None


class TaskManager:
    def __init__(self):
        self._tasks: dict[str, TaskInfo] = {}

    def create_task(self) -> str:
        task_id = str(uuid.uuid4())
        self._tasks[task_id] = TaskInfo(task_id=task_id)
        return task_id

    def get_task(self, task_id: str) -> TaskInfo | None:
        return self._tasks.get(task_id)

    async def run_parse(self, task_id: str, file_path: Path, strategy: str, language: str):
        info = self._tasks.get(task_id)
        if info is None:
            return
        info.status = TaskStatus.PROCESSING
        try:
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(None, parser_factory.parse, file_path, strategy, language)
            info.result = result
            info.status = TaskStatus.COMPLETED
            info.progress = 1.0
        except Exception as e:
            info.status = TaskStatus.FAILED
            info.error_message = str(e)


task_manager = TaskManager()
