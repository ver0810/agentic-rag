from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    app_name: str = "Document Parser Service"
    app_version: str = "0.1.0"
    debug: bool = False

    host: str = "0.0.0.0"
    port: int = 8000

    upload_dir: Path = Path("uploads")
    temp_dir: Path = Path("temp")
    model_dir: Path = Path("models")

    max_file_size_mb: int = 100
    default_strategy: str = "smart"
    default_language: str = "zh"
    render_dpi: int = 216

    auto_download_models: bool = False

    model_config = {"env_prefix": "PARSER_", "env_file": ".env"}


settings = Settings()
