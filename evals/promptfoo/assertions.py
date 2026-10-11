import json
from pathlib import Path

from research_agents.citations import cited_ids_in_text

FIXTURES = Path(__file__).resolve().parent / "fixtures"


def _report_and_known_ids(output, context):
    report = json.loads(output)
    fixture = json.loads((FIXTURES / context["vars"]["fixture"]).read_text(encoding="utf-8"))
    return report, {source["arxivId"] for source in fixture["sources"]}


def _inline_ids(report):
    return cited_ids_in_text(" ".join([report["summary"], *(section["body"] for section in report["sections"])]))


def citations_are_grounded(output, context):
    report, known = _report_and_known_ids(output, context)
    cited = _inline_ids(report) | set(report["citedIds"])
    unknown = sorted(cited - known)
    if unknown:
        return {"pass": False, "score": 0.0, "reason": f"Cites ids outside the sources: {unknown}"}
    return {"pass": True, "score": 1.0, "reason": f"All {len(cited)} citations are grounded"}


def source_coverage(output, context):
    report, known = _report_and_known_ids(output, context)
    covered = len(_inline_ids(report) & known) / len(known)
    return {"pass": covered >= 0.6, "score": covered, "reason": f"Cites {covered:.0%} of the provided sources"}
