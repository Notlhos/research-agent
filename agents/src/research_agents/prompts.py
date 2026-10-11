from research_agents.contracts import Source
from research_agents.ports import JsonRequest

SEARCH_PLAN = "search_plan"
REPORT = "report"

PLANNER_SYSTEM = (
    "You plan literature searches on arXiv. Given a research topic, write between 3 and 5 short search "
    "queries that together cover its main aspects: core methods, recent advances, evaluation and open "
    "problems. Use plain keywords that work well with the arXiv search API, without boolean operators "
    "or field prefixes."
)

WRITER_SYSTEM = (
    "You are a research analyst writing a concise literature report for software engineers. Use only "
    "the sources provided by the user. Cite sources inline with their arXiv id in square brackets, for "
    "example [2401.01234]. Every claim that comes from a source must carry a citation. If the sources "
    "do not cover part of the topic, say so briefly instead of guessing. List every id you cited in "
    "cited_ids."
)

SEARCH_PLAN_SCHEMA = {
    "type": "object",
    "properties": {"queries": {"type": "array", "items": {"type": "string"}}},
    "required": ["queries"],
    "additionalProperties": False,
}

REPORT_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string"},
        "summary": {"type": "string"},
        "sections": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"heading": {"type": "string"}, "body": {"type": "string"}},
                "required": ["heading", "body"],
                "additionalProperties": False,
            },
        },
        "cited_ids": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["title", "summary", "sections", "cited_ids"],
    "additionalProperties": False,
}


def planner_request(topic: str) -> JsonRequest:
    return JsonRequest(
        name=SEARCH_PLAN,
        system=PLANNER_SYSTEM,
        prompt=f"Research topic: {topic}",
        schema=SEARCH_PLAN_SCHEMA,
        max_tokens=2000,
    )


def writer_request(topic: str, sources: list[Source]) -> JsonRequest:
    formatted = "\n\n".join(_format_source(source) for source in sources)
    return JsonRequest(
        name=REPORT,
        system=WRITER_SYSTEM,
        prompt=f"Topic: {topic}\n\nSources:\n\n{formatted}",
        schema=REPORT_SCHEMA,
    )


def _format_source(source: Source) -> str:
    return f"[{source.arxiv_id}] {source.title} ({source.published})\n{source.summary}"
