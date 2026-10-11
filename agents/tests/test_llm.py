from types import SimpleNamespace

import pytest

from research_agents.llm import FALLBACK_BETA, ClaudeJsonLlm, LlmOutputError, LlmRefusalError
from research_agents.models import TokenUsage
from research_agents.ports import JsonRequest

REQUEST = JsonRequest(
    name="report", system="system", prompt="prompt", schema={"type": "object"}, max_tokens=1000
)


class FakeMessages:
    def __init__(self, response: SimpleNamespace) -> None:
        self._response = response
        self.kwargs: dict = {}

    def create(self, **kwargs):
        self.kwargs = kwargs
        return self._response


def make_llm(stop_reason: str = "end_turn", stop_details=None) -> tuple[ClaudeJsonLlm, FakeMessages]:
    response = SimpleNamespace(
        model="claude-opus-5",
        stop_reason=stop_reason,
        stop_details=stop_details,
        content=[SimpleNamespace(type="text", text='{"ok": true}')],
        usage=SimpleNamespace(
            input_tokens=10, output_tokens=5, cache_read_input_tokens=7, cache_creation_input_tokens=0
        ),
    )
    messages = FakeMessages(response)
    client = SimpleNamespace(beta=SimpleNamespace(messages=messages))
    return ClaudeJsonLlm("claude-opus-5", "high", 90.0, client=client), messages


def test_sends_schema_fallbacks_and_caching():
    llm, messages = make_llm()
    usage = TokenUsage()

    text = llm.complete(REQUEST, [{"role": "user", "content": "prompt"}], usage)

    assert text == '{"ok": true}'
    sent = messages.kwargs
    assert sent["model"] == "claude-opus-5"
    assert sent["betas"] == [FALLBACK_BETA]
    assert sent["fallbacks"] == "default"
    assert sent["cache_control"] == {"type": "ephemeral"}
    assert sent["output_config"]["format"] == {"type": "json_schema", "schema": {"type": "object"}}
    assert usage.cache_read_input_tokens == 7


def test_refusal_is_raised_with_category():
    llm, _ = make_llm("refusal", SimpleNamespace(category="cyber"))

    with pytest.raises(LlmRefusalError, match="cyber"):
        llm.complete(REQUEST, [], TokenUsage())


def test_truncated_output_is_raised():
    llm, _ = make_llm("max_tokens")

    with pytest.raises(LlmOutputError, match="truncated"):
        llm.complete(REQUEST, [], TokenUsage())
