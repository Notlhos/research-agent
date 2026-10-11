import json

from research_agents.contracts import EmbedPurpose, EmbedRequest, PlanResponse, WriteRequest


def test_messages_use_camel_case_on_the_wire():
    response = PlanResponse(run_id="r1", attempt=2, queries=["rag"])

    wire = json.loads(response.to_json())

    assert wire["runId"] == "r1"
    assert wire["usage"]["inputTokens"] == 0
    assert wire["error"] is None


def test_requests_parse_java_payloads():
    embed = EmbedRequest.model_validate_json(
        '{"runId":"r1","attempt":1,"purpose":"QUERY","items":[{"id":"topic","text":"agents"}]}'
    )
    write = WriteRequest.model_validate_json(
        '{"runId":"r1","attempt":1,"topic":"t","sources":'
        '[{"arxivId":"2401.00001","title":"T","summary":"S","published":"2024-01-01"}]}'
    )

    assert embed.purpose is EmbedPurpose.QUERY
    assert write.sources[0].arxiv_id == "2401.00001"
