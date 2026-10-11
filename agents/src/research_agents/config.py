from enum import StrEnum

from pydantic_settings import BaseSettings, SettingsConfigDict


class LlmProvider(StrEnum):
    CLAUDE = "claude"
    FAKE = "fake"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RA_", env_file=".env", extra="ignore")

    kafka_bootstrap_servers: str = "localhost:29092"
    consumer_group_prefix: str = "research-agents"
    llm_provider: LlmProvider = LlmProvider.CLAUDE
    llm_model: str = "claude-opus-5"
    llm_effort: str = "high"
    llm_timeout_seconds: float = 90.0
    embedding_model: str = "BAAI/bge-small-en-v1.5"
    max_correction_attempts: int = 3
    metrics_port: int = 9100
