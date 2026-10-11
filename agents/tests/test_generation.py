import pytest

from research_agents.generation import generate
from research_agents.llm import LlmOutputError
from research_agents.models import SearchPlan, TokenUsage
from research_agents.ports import JsonRequest

from .fakes import ScriptedLlm

REQUEST = JsonRequest(name="search_plan", system="system", prompt="topic", schema={"type": "object"})


def test_returns_first_valid_answer():
    llm = ScriptedLlm([{"queries": ["rag evaluation"]}])
    usage = TokenUsage()

    plan = generate(llm, REQUEST, SearchPlan, usage, attempts=3)

    assert plan.queries == ["rag evaluation"]
    assert usage.llm_calls == 1


def test_feeds_problems_back_and_retries():
    llm = ScriptedLlm([{"queries": ["bad"]}, {"queries": ["good"]}])

    def reject_bad(plan: SearchPlan) -> list[str]:
        return ["query 'bad' is too vague"] if "bad" in plan.queries else []

    plan = generate(llm, REQUEST, SearchPlan, TokenUsage(), attempts=3, check=reject_bad)

    assert plan.queries == ["good"]
    retry = llm.conversations[1]
    assert [message["role"] for message in retry] == ["user", "assistant", "user"]
    assert "too vague" in retry[-1]["content"]


def test_schema_violations_are_retried():
    llm = ScriptedLlm([{"queries": []}, {"queries": ["agents"]}])

    plan = generate(llm, REQUEST, SearchPlan, TokenUsage(), attempts=2)

    assert plan.queries == ["agents"]


def test_gives_up_after_attempts():
    llm = ScriptedLlm([{"queries": []}, {"queries": []}])

    with pytest.raises(LlmOutputError, match="after 2 attempts"):
        generate(llm, REQUEST, SearchPlan, TokenUsage(), attempts=2)
