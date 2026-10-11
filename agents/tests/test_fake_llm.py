from research_agents.citations import citation_problems
from research_agents.fake_llm import FakeJsonLlm
from research_agents.generation import generate
from research_agents.models import Report, SearchPlan, TokenUsage
from research_agents.prompts import planner_request, writer_request

from .fakes import make_source


def test_fake_plan_is_valid():
    request = planner_request("llm agents")
    usage = TokenUsage()

    plan = generate(FakeJsonLlm(), request, SearchPlan, usage, attempts=1)

    assert plan.queries[0] == "llm agents"
    assert usage.llm_calls == 1 and usage.input_tokens > 0


def test_fake_report_passes_citation_checks():
    sources = [make_source("2401.00001", "Agents"), make_source("hep-th/9901001", "Strings")]
    known = {source.arxiv_id for source in sources}

    report = generate(
        FakeJsonLlm(),
        writer_request("llm agents", sources),
        Report,
        TokenUsage(),
        attempts=1,
        check=lambda candidate: citation_problems(candidate, known),
    )

    assert set(report.cited_ids) == known
