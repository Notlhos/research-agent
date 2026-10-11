from collections.abc import Callable
from typing import Protocol

from research_agents.citations import citation_problems
from research_agents.contracts import (
    EMBED_REQUEST_TOPIC,
    EMBED_RESPONSE_TOPIC,
    PLAN_REQUEST_TOPIC,
    PLAN_RESPONSE_TOPIC,
    WRITE_REQUEST_TOPIC,
    WRITE_RESPONSE_TOPIC,
    AgentRequest,
    AgentResponse,
    EmbedPurpose,
    EmbedRequest,
    EmbedResponse,
    EmbedVector,
    LlmUsage,
    PlanRequest,
    PlanResponse,
    ReportPayload,
    WriteRequest,
    WriteResponse,
)
from research_agents.generation import generate
from research_agents.models import Report, SearchPlan, TokenUsage
from research_agents.ports import Embedder, JsonLlm
from research_agents.prompts import planner_request, writer_request


class Handler(Protocol):
    name: str
    request_topic: str
    response_topic: str

    def parse(self, payload: bytes) -> AgentRequest: ...

    def process(self, request: AgentRequest) -> AgentResponse: ...

    def failure(self, request: AgentRequest, error: Exception) -> AgentResponse: ...


def describe(error: Exception) -> str:
    return f"{type(error).__name__}: {error}"


class PlanHandler:
    name = "planner"
    request_topic = PLAN_REQUEST_TOPIC
    response_topic = PLAN_RESPONSE_TOPIC

    def __init__(self, llm: JsonLlm, correction_attempts: int) -> None:
        self._llm = llm
        self._attempts = correction_attempts

    def parse(self, payload: bytes) -> PlanRequest:
        return PlanRequest.model_validate_json(payload)

    def process(self, request: PlanRequest) -> PlanResponse:
        usage = TokenUsage()
        plan = generate(self._llm, planner_request(request.topic), SearchPlan, usage, self._attempts)
        return PlanResponse(
            run_id=request.run_id, attempt=request.attempt, queries=plan.queries, usage=LlmUsage.of(usage)
        )

    def failure(self, request: PlanRequest, error: Exception) -> PlanResponse:
        return PlanResponse(run_id=request.run_id, attempt=request.attempt, error=describe(error))


class WriteHandler:
    name = "writer"
    request_topic = WRITE_REQUEST_TOPIC
    response_topic = WRITE_RESPONSE_TOPIC

    def __init__(self, llm: JsonLlm, correction_attempts: int) -> None:
        self._llm = llm
        self._attempts = correction_attempts

    def parse(self, payload: bytes) -> WriteRequest:
        return WriteRequest.model_validate_json(payload)

    def process(self, request: WriteRequest) -> WriteResponse:
        usage = TokenUsage()
        known_ids = {source.arxiv_id for source in request.sources}
        report = generate(
            self._llm,
            writer_request(request.topic, request.sources),
            Report,
            usage,
            self._attempts,
            check=lambda candidate: citation_problems(candidate, known_ids),
        )
        return WriteResponse(
            run_id=request.run_id,
            attempt=request.attempt,
            report=ReportPayload.of(report),
            usage=LlmUsage.of(usage),
        )

    def failure(self, request: WriteRequest, error: Exception) -> WriteResponse:
        return WriteResponse(run_id=request.run_id, attempt=request.attempt, error=describe(error))


class EmbedHandler:
    name = "embedder"
    request_topic = EMBED_REQUEST_TOPIC
    response_topic = EMBED_RESPONSE_TOPIC

    def __init__(self, embedder: Embedder) -> None:
        self._strategies: dict[EmbedPurpose, Callable[[list[str]], list[list[float]]]] = {
            EmbedPurpose.DOCUMENTS: embedder.embed_documents,
            EmbedPurpose.QUERY: lambda texts: [embedder.embed_query(text) for text in texts],
        }

    def parse(self, payload: bytes) -> EmbedRequest:
        return EmbedRequest.model_validate_json(payload)

    def process(self, request: EmbedRequest) -> EmbedResponse:
        vectors = self._strategies[request.purpose]([item.text for item in request.items])
        return EmbedResponse(
            run_id=request.run_id,
            attempt=request.attempt,
            purpose=request.purpose,
            vectors=[
                EmbedVector(id=item.id, vector=vector)
                for item, vector in zip(request.items, vectors, strict=True)
            ],
        )

    def failure(self, request: EmbedRequest, error: Exception) -> EmbedResponse:
        return EmbedResponse(
            run_id=request.run_id, attempt=request.attempt, purpose=request.purpose, error=describe(error)
        )
