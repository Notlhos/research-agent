import pytest

from research_agents.contracts import PLAN_REQUEST_TOPIC, PLAN_RESPONSE_TOPIC, dead_letter_topic
from research_agents.handlers import PlanHandler
from research_agents.metrics import RoleMetrics
from research_agents.tracing import NoopTracer
from research_agents.worker import KafkaWorker, ResponseNotDeliveredError

from .fakes import FakeConsumer, FakeMessage, FakeProducer, ScriptedLlm


def make_worker(
    messages: list[FakeMessage], replies: list, producer: FakeProducer | None = None
) -> tuple[KafkaWorker, FakeConsumer, FakeProducer]:
    consumer, producer = FakeConsumer(messages), producer or FakeProducer()
    handler = PlanHandler(ScriptedLlm(replies), correction_attempts=1)
    return KafkaWorker(handler, consumer, producer, NoopTracer(), RoleMetrics("planner")), consumer, producer


def test_request_produces_response_and_commits():
    message = FakeMessage(b'{"runId":"r1","attempt":1,"topic":"agents"}', key=b"r1")
    worker, consumer, producer = make_worker([message], [{"queries": ["agents"]}])

    worker.poll_once()

    topic, payload, key = producer.sent[0]
    assert topic == PLAN_RESPONSE_TOPIC
    assert payload["queries"] == ["agents"]
    assert key == b"r1"
    assert consumer.committed == [message]


def test_handler_exception_becomes_error_response():
    message = FakeMessage(b'{"runId":"r1","attempt":2,"topic":"agents"}')
    worker, _, producer = make_worker([message], ["not json"])

    worker.poll_once()

    payload = producer.sent[0][1]
    assert payload["attempt"] == 2
    assert "LlmOutputError" in payload["error"]


def test_malformed_payload_goes_to_dead_letter_topic_and_is_committed():
    message = FakeMessage(b"not json", key=b"r1")
    worker, consumer, producer = make_worker([message], [])

    worker.poll_once()

    assert producer.raw == [(dead_letter_topic(PLAN_REQUEST_TOPIC), b"not json", b"r1")]
    assert consumer.committed == [message]


def test_undelivered_response_is_not_committed():
    message = FakeMessage(b'{"runId":"r1","attempt":1,"topic":"agents"}')
    worker, consumer, _ = make_worker([message], [{"queries": ["agents"]}], FakeProducer(undelivered=1))

    with pytest.raises(ResponseNotDeliveredError):
        worker.poll_once()

    assert consumer.committed == []
