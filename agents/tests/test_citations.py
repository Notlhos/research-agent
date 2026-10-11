from research_agents.citations import citation_problems, inline_citations
from research_agents.models import Report, ReportSection


def make_report(body: str, cited_ids: list[str]) -> Report:
    return Report(
        title="Title",
        summary="Summary [2401.00001].",
        sections=[ReportSection(heading="Findings", body=body)],
        cited_ids=cited_ids,
    )


def test_inline_citations_support_grouped_ids():
    report = make_report("Two results agree [2401.00002, 2401.00003].", ["2401.00001"])

    assert inline_citations(report) == {"2401.00001", "2401.00002", "2401.00003"}


def test_valid_report_has_no_problems():
    report = make_report("Agents improve [2401.00002].", ["2401.00001", "2401.00002"])

    assert citation_problems(report, {"2401.00001", "2401.00002"}) == []


def test_unknown_and_unlisted_ids_are_reported():
    report = make_report("A claim [9999.99999].", ["2401.00001"])

    problems = citation_problems(report, {"2401.00001"})

    assert any("9999.99999" in problem and "not among" in problem for problem in problems)
    assert any("cited_ids is missing" in problem for problem in problems)


def test_report_without_citations_is_rejected():
    report = Report(
        title="Title",
        summary="No sources here.",
        sections=[ReportSection(heading="Findings", body="Nothing cited.")],
        cited_ids=["2401.00001"],
    )

    assert any("no inline citations" in problem for problem in citation_problems(report, {"2401.00001"}))


def test_inline_citations_ignore_brackets_that_are_not_arxiv_ids():
    report = make_report(
        "See [Table 2] and [the docs](https://example.org); "
        "results [1] agree [2401.00002; hep-th/9901001v2].",
        ["2401.00001"],
    )

    assert inline_citations(report) == {"2401.00001", "2401.00002", "hep-th/9901001v2"}
