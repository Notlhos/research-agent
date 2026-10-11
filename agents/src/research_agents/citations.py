import re

from research_agents.models import Report

BRACKET_GROUP = re.compile(r"\[([^\[\]]+)\](?!\()")
ARXIV_ID = re.compile(r"(?<![\w./])(?:\d{4}\.\d{4,5}|[a-z][a-z-]*(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?(?![\w.])")


def cited_ids_in_text(text: str) -> set[str]:
    return {arxiv_id for group in BRACKET_GROUP.findall(text) for arxiv_id in ARXIV_ID.findall(group)}


def inline_citations(report: Report) -> set[str]:
    return cited_ids_in_text(" ".join([report.summary, *(section.body for section in report.sections)]))


def citation_problems(report: Report, known_ids: set[str]) -> list[str]:
    inline = inline_citations(report)
    listed = set(report.cited_ids)
    problems = []
    if not inline:
        problems.append("The report has no inline citations. Cite sources as [arxiv_id].")
    unknown = sorted((inline | listed) - known_ids)
    if unknown:
        problems.append(f"These ids are not among the provided sources: {', '.join(unknown)}.")
    missing = sorted(inline - listed)
    if missing:
        problems.append(f"cited_ids is missing ids that are cited inline: {', '.join(missing)}.")
    return problems
