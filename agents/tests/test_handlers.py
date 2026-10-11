from research_agents.contracts import (
    EmbedItem,
    EmbedPurpose,
    EmbedRequest,
    PlanRequest,
    WriteRequest,
)
from research_agents.handlers import EmbedHandler, PlanHandler, WriteHandler

from .fakes import FixedEmbedder, ScriptedLlm, make_source


def test_plan_handler_returns_queries_and_usage():
    handler = PlanHandler(ScriptedLlm([{"queries": ["a", "b"]}]), correction_attempts=2)

    response = handler.process(PlanRequest(run_id="r1", attempt=1, topic="agents"))

    assert response.queries == ["a", "b"]
    assert response.usage.llm_calls == 1
    assert response.error is None


def test_write_handler_rejects_unknown_citations_then_accepts_fix():
    bad = {
        "title": "T",
        "summary": "S [9999.99999].",
        "sections": [{"heading": "H", "body": "B [2401.00001]."}],
        "cited_ids": ["2401.00001", "9999.99999"],
    }
    good = {**bad, "summary": "S [2401.00001].", "cited_ids": ["2401.00001"]}
    llm = ScriptedLlm([bad, good])
    handler = WriteHandler(llm, correction_attempts=2)

    response = handler.process(
        WriteRequest(run_id="r1", attempt=1, topic="agents", sources=[make_source("2401.00001")])
    )

    assert response.report.cited_ids == ["2401.00001"]
    assert response.usage.llm_calls == 2


def test_embed_handler_uses_query_strategy():
    handler = EmbedHandler(FixedEmbedder())
    request = EmbedRequest(
        run_id="r1", attempt=1, purpose=EmbedPurpose.QUERY, items=[EmbedItem(id="topic", text="agents")]
    )

    response = handler.process(request)

    assert response.vectors[0].id == "topic"
    assert response.vectors[0].vector == [0.0, 6.0]


def test_failure_keeps_run_and_attempt():
    handler = PlanHandler(ScriptedLlm([]), correction_attempts=1)

    response = handler.failure(PlanRequest(run_id="r9", attempt=3, topic="t"), RuntimeError("boom"))

    assert (response.run_id, response.attempt, response.error) == ("r9", 3, "RuntimeError: boom")
