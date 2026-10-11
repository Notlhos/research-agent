import json
from types import SimpleNamespace
from typing import Any

from research_agents.contracts import Source
from research_agents.models import TokenUsage
from research_agents.ports import JsonRequest


def make_source(arxiv_id: str, title: str = "A paper") -> Source:
    return Source(arxiv_id=arxiv_id, title=title, summary="An abstract.", published="2025-01-15")


class ScriptedLlm:
    def __init__(self, replies: list[Any]) -> None:
        self._replies = [reply if isinstance(reply, str) else json.dumps(reply) for reply in replies]
        self.requests: list[JsonRequest] = []
        self.conversations: list[list[dict[str, Any]]] = []

    def complete(self, request: JsonRequest, messages: list[dict[str, Any]], usage: TokenUsage) -> str:
        self.requests.append(request)
        self.conversations.append([dict(message) for message in messages])
        usage.add(SimpleNamespace(input_tokens=100, output_tokens=50))
        return self._replies.pop(0)


class FixedEmbedder:
    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[float(len(text)), 1.0] for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return [0.0, float(len(text))]


class FakeMessage:
    def __init__(self, value: bytes, key: bytes | None = b"run", error: Any = None) -> None:
        self._value = value
        self._key = key
        self._error = error

    def value(self) -> bytes:
        return self._value

    def key(self) -> bytes | None:
        return self._key

    def error(self) -> Any:
        return self._error


class FakeConsumer:
    def __init__(self, messages: list[FakeMessage]) -> None:
        self._messages = list(messages)
        self.committed: list[FakeMessage] = []
        self.topics: list[str] = []

    def subscribe(self, topics: list[str]) -> None:
        self.topics = topics

    def poll(self, timeout: float) -> FakeMessage | None:
        return self._messages.pop(0) if self._messages else None

    def commit(self, message: FakeMessage, asynchronous: bool) -> None:
        self.committed.append(message)

    def close(self) -> None:
        return None


class FakeProducer:
    def __init__(self, undelivered: int = 0) -> None:
        self.raw: list[tuple[str, bytes, bytes | None]] = []
        self._undelivered = undelivered

    @property
    def sent(self) -> list[tuple[str, dict, bytes | None]]:
        return [(topic, json.loads(value), key) for topic, value, key in self.raw]

    def produce(self, topic: str, value: bytes, key: bytes | None = None) -> None:
        self.raw.append((topic, value, key))

    def flush(self, timeout: float) -> int:
        return self._undelivered
