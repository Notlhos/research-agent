from contextlib import AbstractContextManager
from dataclasses import dataclass
from typing import Any, Protocol

from research_agents.models import TokenUsage


@dataclass(frozen=True)
class JsonRequest:
    name: str
    system: str
    prompt: str
    schema: dict[str, Any]
    max_tokens: int = 16000


class JsonLlm(Protocol):
    def complete(self, request: JsonRequest, messages: list[dict[str, Any]], usage: TokenUsage) -> str: ...


class Embedder(Protocol):
    def embed_documents(self, texts: list[str]) -> list[list[float]]: ...

    def embed_query(self, text: str) -> list[float]: ...


class Tracer(Protocol):
    def observe(
        self, name: str, as_type: str, trace_id: str | None = None, **attributes: Any
    ) -> AbstractContextManager: ...

    def record_generation(self, model: str, usage: dict[str, int], output: str) -> None: ...

    def flush(self) -> None: ...
