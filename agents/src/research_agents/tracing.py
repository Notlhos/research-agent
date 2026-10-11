import os
from contextlib import AbstractContextManager, nullcontext
from typing import Any

from research_agents.ports import Tracer


class NoopTracer:
    def observe(
        self, name: str, as_type: str, trace_id: str | None = None, **attributes: Any
    ) -> AbstractContextManager:
        return nullcontext()

    def record_generation(self, model: str, usage: dict[str, int], output: str) -> None:
        return None

    def flush(self) -> None:
        return None


class LangfuseTracer:
    """Langfuse tracing where every agent call of one research run lands in the same trace.

    The run id is a UUID, and its 32 hex digits form a valid OpenTelemetry trace id, so planner, embedder
    and writer spans emitted by different processes are grouped under one trace without the orchestrator
    having to pass tracing headers through Kafka.
    """

    def __init__(self) -> None:
        from langfuse import Langfuse

        self._client = Langfuse()

    def observe(
        self, name: str, as_type: str, trace_id: str | None = None, **attributes: Any
    ) -> AbstractContextManager:
        trace_context = {"trace_id": trace_id.replace("-", "")} if trace_id else None
        return self._client.start_as_current_observation(
            name=name, as_type=as_type, trace_context=trace_context, **attributes
        )

    def record_generation(self, model: str, usage: dict[str, int], output: str) -> None:
        self._client.update_current_generation(model=model, usage_details=usage, output=output)

    def flush(self) -> None:
        self._client.flush()


def build_tracer() -> Tracer:
    configured = os.getenv("LANGFUSE_PUBLIC_KEY") and os.getenv("LANGFUSE_SECRET_KEY")
    return LangfuseTracer() if configured else NoopTracer()
