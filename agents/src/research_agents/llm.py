from typing import Any

import anthropic

from research_agents.models import TokenUsage
from research_agents.ports import JsonRequest, Tracer
from research_agents.tracing import NoopTracer

FALLBACK_BETA = "server-side-fallback-2026-07-01"


class LlmRefusalError(RuntimeError):
    pass


class LlmOutputError(RuntimeError):
    pass


class ClaudeJsonLlm:
    """Claude client that always returns schema-constrained JSON text.

    Requests go through the beta endpoint only to enable server-side refusal fallbacks
    (`fallbacks="default"`): a request declined by a safety classifier is re-run on Anthropic's
    recommended fallback model inside the same call instead of failing the whole research run.

    SDK retries are disabled and every call has a hard timeout: retries belong to the orchestrator, and
    `max_correction_attempts * timeout` must stay below the orchestrator's step timeout, otherwise a slow step
    is re-dispatched while the first attempt is still running and its tokens are paid twice.
    """

    def __init__(
        self,
        model: str,
        effort: str,
        timeout_seconds: float,
        client: anthropic.Anthropic | None = None,
        tracer: Tracer | None = None,
    ) -> None:
        self._client = client or anthropic.Anthropic(max_retries=0, timeout=timeout_seconds)
        self._model = model
        self._effort = effort
        self._tracer = tracer or NoopTracer()

    def complete(self, request: JsonRequest, messages: list[dict[str, Any]], usage: TokenUsage) -> str:
        with self._tracer.observe(request.name, "generation", model=self._model, input=messages):
            response = self._client.beta.messages.create(
                model=self._model,
                max_tokens=request.max_tokens,
                betas=[FALLBACK_BETA],
                fallbacks="default",
                cache_control={"type": "ephemeral"},
                system=request.system,
                messages=messages,
                output_config={
                    "effort": self._effort,
                    "format": {"type": "json_schema", "schema": request.schema},
                },
            )
            usage.add(response.usage)
            _raise_for_stop_reason(response)
            text = next(block.text for block in response.content if block.type == "text")
            self._tracer.record_generation(response.model, _usage_details(response.usage), text)
            return text


def _raise_for_stop_reason(response: Any) -> None:
    if response.stop_reason == "refusal":
        category = response.stop_details.category if response.stop_details else None
        raise LlmRefusalError(f"Model declined the request (category: {category})")
    if response.stop_reason == "max_tokens":
        raise LlmOutputError("Model output was truncated by max_tokens")


def _usage_details(usage: Any) -> dict[str, int]:
    return {
        "input": usage.input_tokens or 0,
        "output": usage.output_tokens or 0,
        "cache_read_input_tokens": getattr(usage, "cache_read_input_tokens", 0) or 0,
    }
