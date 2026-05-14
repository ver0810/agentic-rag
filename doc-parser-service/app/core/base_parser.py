from abc import ABC, abstractmethod
from pathlib import Path
from app.models.response import ParseResult


class BaseParser(ABC):
    @abstractmethod
    def parse(self, file_path: Path, strategy: str, language: str, **kwargs) -> ParseResult:
        ...

    @abstractmethod
    def supports(self, file_extension: str, strategy: str) -> bool:
        ...

    def order(self) -> int:
        return 0
