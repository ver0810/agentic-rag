from pathlib import Path
from app.core.base_parser import BaseParser
from app.models.response import ParseResult
from app.processors.pdf_processor import PdfProcessor
from app.processors.word_processor import WordProcessor
from app.processors.excel_processor import ExcelProcessor
from app.processors.ppt_processor import PptProcessor
from app.processors.image_processor import ImageProcessor


class ParserFactory:
    def __init__(self):
        self._parsers: list[BaseParser] = []

    def register(self, parser: BaseParser):
        self._parsers.append(parser)
        self._parsers.sort(key=lambda p: p.order(), reverse=True)

    def get_parser(self, file_extension: str, strategy: str) -> BaseParser | None:
        for parser in self._parsers:
            if parser.supports(file_extension, strategy):
                return parser
        return None

    def parse(self, file_path: Path, strategy: str, language: str) -> ParseResult:
        ext = file_path.suffix.lower().lstrip(".")
        parser = self.get_parser(ext, strategy)
        if parser is None:
            return ParseResult(
                success=False,
                error_message=f"No parser available for extension: {ext}, strategy: {strategy}",
            )
        return parser.parse(file_path, strategy, language)


parser_factory = ParserFactory()
parser_factory.register(PdfProcessor())
parser_factory.register(WordProcessor())
parser_factory.register(ExcelProcessor())
parser_factory.register(PptProcessor())
parser_factory.register(ImageProcessor())
