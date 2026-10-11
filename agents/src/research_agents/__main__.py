import argparse
import logging
import signal
import threading
from collections.abc import Callable

from confluent_kafka import Consumer, Producer
from prometheus_client import start_http_server

from research_agents.config import LlmProvider, Settings
from research_agents.fake_llm import FakeJsonLlm
from research_agents.handlers import EmbedHandler, Handler, PlanHandler, WriteHandler
from research_agents.llm import ClaudeJsonLlm
from research_agents.metrics import RoleMetrics
from research_agents.ports import JsonLlm, Tracer
from research_agents.tracing import build_tracer

MAX_POLL_INTERVAL_MS = 15 * 60 * 1000


def build_llm(settings: Settings, tracer: Tracer) -> JsonLlm:
    providers: dict[LlmProvider, Callable[[], JsonLlm]] = {
        LlmProvider.CLAUDE: lambda: ClaudeJsonLlm(
            settings.llm_model, settings.llm_effort, settings.llm_timeout_seconds, tracer=tracer
        ),
        LlmProvider.FAKE: FakeJsonLlm,
    }
    return providers[settings.llm_provider]()


def build_planner(settings: Settings, tracer: Tracer) -> Handler:
    return PlanHandler(build_llm(settings, tracer), settings.max_correction_attempts)


def build_writer(settings: Settings, tracer: Tracer) -> Handler:
    return WriteHandler(build_llm(settings, tracer), settings.max_correction_attempts)


def build_embedder(settings: Settings, tracer: Tracer) -> Handler:
    from research_agents.embeddings import FastEmbedEmbedder

    return EmbedHandler(FastEmbedEmbedder(settings.embedding_model))


ROLES: dict[str, Callable[[Settings, Tracer], Handler]] = {
    "planner": build_planner,
    "writer": build_writer,
    "embedder": build_embedder,
}


def main() -> None:
    parser = argparse.ArgumentParser(prog="research-agents", description="Run one Research Agent worker.")
    parser.add_argument("--role", choices=sorted(ROLES), required=True)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    from research_agents.worker import KafkaWorker

    settings = Settings()
    start_http_server(settings.metrics_port)
    tracer = build_tracer()
    handler = ROLES[args.role](settings, tracer)
    consumer = Consumer(
        {
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "group.id": f"{settings.consumer_group_prefix}-{args.role}",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
            "max.poll.interval.ms": MAX_POLL_INTERVAL_MS,
        }
    )
    producer = Producer({"bootstrap.servers": settings.kafka_bootstrap_servers, "acks": "all"})
    logging.getLogger(__name__).info("Starting %s with LLM provider %s", args.role, settings.llm_provider)

    stop = threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stop.set())
    signal.signal(signal.SIGINT, lambda *_: stop.set())
    KafkaWorker(handler, consumer, producer, tracer, RoleMetrics(args.role)).run(stop)


if __name__ == "__main__":
    main()
