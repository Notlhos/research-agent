import logging
import threading
import time
from typing import Any, Protocol

from pydantic import ValidationError

from research_agents.contracts import dead_letter_topic
from research_agents.handlers import Handler
from research_agents.metrics import RoleMetrics
from research_agents.ports import Tracer

log = logging.getLogger(__name__)

FLUSH_TIMEOUT_SECONDS = 10.0


class ResponseNotDeliveredError(RuntimeError):
    pass


class Consumer(Protocol):
    def subscribe(self, topics: list[str]) -> None: ...

    def poll(self, timeout: float) -> Any: ...

    def commit(self, message: Any, asynchronous: bool) -> Any: ...

    def close(self) -> None: ...


class Producer(Protocol):
    def produce(self, topic: str, value: bytes, key: bytes | None = None) -> None: ...

    def flush(self, timeout: float) -> int: ...


class KafkaWorker:
    """Consumes agent requests and always answers with a response message, success or failure.

    The orchestrator owns retries and timeouts, so a worker never retries by itself: any exception becomes an
    error response for that run and attempt. Offsets are committed only after the broker has acknowledged
    the response, which gives at-least-once delivery; the orchestrator discards duplicate or late responses
    by run id and attempt. If the response cannot be delivered the worker stops without committing, so after
    a restart the request is consumed again. A request that cannot be parsed has no run to answer for, so it
    is moved to the dead-letter topic as is.
    """

    def __init__(
        self, handler: Handler, consumer: Consumer, producer: Producer, tracer: Tracer, metrics: RoleMetrics
    ) -> None:
        self._handler = handler
        self._consumer = consumer
        self._producer = producer
        self._tracer = tracer
        self._metrics = metrics

    def run(self, stop: threading.Event) -> None:
        self._consumer.subscribe([self._handler.request_topic])
        log.info("%s listening on %s", self._handler.name, self._handler.request_topic)
        try:
            while not stop.is_set():
                self.poll_once()
        finally:
            self._consumer.close()
            self._producer.flush(FLUSH_TIMEOUT_SECONDS)
            self._tracer.flush()

    def poll_once(self) -> None:
        message = self._consumer.poll(1.0)
        if message is None:
            return
        if message.error():
            log.warning("Kafka error: %s", message.error())
            return
        self._process(message)
        self._consumer.commit(message=message, asynchronous=False)

    def _process(self, message: Any) -> None:
        try:
            request = self._handler.parse(message.value())
        except ValidationError:
            log.exception("Malformed message on %s, moving it to dead letters", self._handler.request_topic)
            self._dead_letter(message)
            return
        started = time.perf_counter()
        response = self._respond(request)
        self._producer.produce(self._handler.response_topic, value=response.to_json(), key=message.key())
        self._flush()
        outcome = "ok" if response.error is None else "error"
        self._metrics.observe(outcome, time.perf_counter() - started, getattr(response, "usage", None))

    def _dead_letter(self, message: Any) -> None:
        topic = dead_letter_topic(self._handler.request_topic)
        self._producer.produce(topic, value=message.value(), key=message.key())
        self._flush()
        self._metrics.observe("invalid", 0.0, None)

    def _flush(self) -> None:
        undelivered = self._producer.flush(FLUSH_TIMEOUT_SECONDS)
        if undelivered:
            raise ResponseNotDeliveredError(f"{undelivered} message(s) not acknowledged by Kafka")

    def _respond(self, request: Any) -> Any:
        with self._tracer.observe(
            self._handler.name,
            "agent",
            trace_id=request.run_id,
            metadata={"runId": request.run_id, "attempt": request.attempt},
        ):
            try:
                return self._handler.process(request)
            except Exception as error:
                log.exception("%s failed for run %s", self._handler.name, request.run_id)
                return self._handler.failure(request, error)
