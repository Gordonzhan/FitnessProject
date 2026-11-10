from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Agent 运行配置；所有字段均可通过 FITNESS_AGENT_ 前缀的环境变量覆盖。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="FITNESS_AGENT_",
        extra="ignore",
    )

    service_name: str = "fitness-ai-agent"
    environment: Literal["local", "test", "staging", "production"] = "local"
    provider: Literal["fake", "ollama"] = "fake"
    # 未单独指定时，菜谱生成沿用视觉 Provider；便于本地只切一次 Provider。
    recipe_provider: Literal["fake", "ollama"] | None = None
    workout_provider: Literal["fake", "ollama"] | None = None
    chat_provider: Literal["fake", "ollama"] | None = None
    recipe_generation_enabled: bool = True
    service_token: SecretStr = SecretStr("")
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
    # 本地环境默认写入滚动文件，便于在控制台关闭后按 requestId 追查模型降级原因。
    # 其他环境由容器日志平台持久化；如需额外文件可显式配置该路径。
    log_file: str | None = None
    request_timeout_seconds: float = Field(default=30.0, gt=0, le=120)

    # Ollama 与图片下载分别设置超时，避免首次加载本地模型时被普通 HTTP 超时提前中断。
    ollama_base_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "qwen3-vl:8b-instruct"
    ollama_timeout_seconds: float = Field(default=180.0, gt=0, le=600)
    ollama_recipe_model: str = "qwen3:8b"
    ollama_recipe_timeout_seconds: float = Field(default=120.0, gt=0, le=600)
    ollama_workout_model: str = "qwen3:8b"
    ollama_workout_timeout_seconds: float = Field(default=120.0, gt=0, le=600)
    ollama_agent_model: str = "qwen3:8b"
    ollama_agent_timeout_seconds: float = Field(default=120.0, gt=0, le=600)
    ollama_agent_keep_alive: str = Field(default="30m", min_length=1, max_length=20)
    java_internal_base_url: str = "http://127.0.0.1:8080/api"
    java_service_token: SecretStr = SecretStr("")
    tool_timeout_seconds: float = Field(default=8.0, gt=0, le=30)
    max_tool_rounds: int = Field(default=6, ge=1, le=6)
    max_image_bytes: int = Field(default=10 * 1024 * 1024, gt=0, le=25 * 1024 * 1024)

    knowledge_enabled: bool = False
    knowledge_directory: str = "knowledge/documents"
    knowledge_chunk_size: int = Field(default=900, ge=200, le=2000)
    knowledge_minimum_score: float = Field(default=0.35, ge=0, le=1)
    embedding_provider: Literal["fake", "ollama"] = "fake"
    embedding_model: str = "nomic-embed-text"
    embedding_dimension: int = Field(default=768, ge=8, le=8192)
    embedding_timeout_seconds: float = Field(default=30.0, gt=0, le=180)
    embedding_batch_size: int = Field(default=32, ge=1, le=128)
    qdrant_url: str = "http://127.0.0.1:6333"
    qdrant_api_key: SecretStr = SecretStr("")
    qdrant_collection: str = "fitness_knowledge_local_v1"
    qdrant_timeout_seconds: float = Field(default=10.0, gt=0, le=60)

    # 仅供本机临时图片服务器联调；测试、预发布和生产环境必须保持关闭。
    allow_private_image_urls: bool = False

    @model_validator(mode="after")
    def validate_service_identity(self) -> Settings:
        """测试及生产环境不能以空或弱令牌暴露内部业务接口。"""

        if self.environment not in {"staging", "production"}:
            return self
        inbound = self.service_token.get_secret_value().encode()
        outbound_value = (
            self.java_service_token.get_secret_value()
            or self.service_token.get_secret_value()
        )
        if len(inbound) < 32:
            raise ValueError("staging/production service token must be at least 32 bytes")
        if len(outbound_value.encode()) < 32:
            raise ValueError("staging/production Java service token must be at least 32 bytes")
        if self.knowledge_enabled and self.qdrant_collection.startswith("fitness_knowledge_local"):
            raise ValueError(
                "staging/production must use an environment-specific Qdrant collection"
            )
        return self


@lru_cache
def get_settings() -> Settings:
    """每个进程只解析一次环境变量，测试可直接向 create_app 注入配置。"""

    return Settings()
