from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from research_agents.models import Report, TokenUsage

PLAN_REQUEST_TOPIC = "research_plan_request"
PLAN_RESPONSE_TOPIC = "research_plan_response"
EMBED_REQUEST_TOPIC = "research_embedding_request"
EMBED_RESPONSE_TOPIC = "research_embedding_response"
WRITE_REQUEST_TOPIC = "research_report_request"
WRITE_RESPONSE_TOPIC = "research_report_response"
DEAD_LETTER_SUFFIX = "_dlt"


def dead_letter_topic(topic: str) -> str:
    return topic + DEAD_LETTER_SUFFIX


class Message(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    def to_json(self) -> bytes:
        return self.model_dump_json(by_alias=True).encode()


class LlmUsage(Message):
    llm_calls: int = 0
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_input_tokens: int = 0
    cache_creation_input_tokens: int = 0

    @classmethod
    def of(cls, usage: TokenUsage) -> "LlmUsage":
        return cls(**usage.model_dump())


class AgentRequest(Message):
    run_id: str
    attempt: int


class AgentResponse(Message):
    run_id: str
    attempt: int
    error: str | None = None


class PlanRequest(AgentRequest):
    topic: str


class PlanResponse(AgentResponse):
    queries: list[str] = Field(default_factory=list)
    usage: LlmUsage = Field(default_factory=LlmUsage)


class EmbedPurpose(StrEnum):
    DOCUMENTS = "DOCUMENTS"
    QUERY = "QUERY"


class EmbedItem(Message):
    id: str
    text: str


class EmbedRequest(AgentRequest):
    purpose: EmbedPurpose
    items: list[EmbedItem]


class EmbedVector(Message):
    id: str
    vector: list[float]


class EmbedResponse(AgentResponse):
    purpose: EmbedPurpose
    vectors: list[EmbedVector] = Field(default_factory=list)


class Source(Message):
    arxiv_id: str
    title: str
    summary: str
    published: str


class WriteRequest(AgentRequest):
    topic: str
    sources: list[Source]


class ReportSectionPayload(Message):
    heading: str
    body: str


class ReportPayload(Message):
    title: str
    summary: str
    sections: list[ReportSectionPayload]
    cited_ids: list[str]

    @classmethod
    def of(cls, report: Report) -> "ReportPayload":
        return cls.model_validate(report.model_dump())


class WriteResponse(AgentResponse):
    report: ReportPayload | None = None
    usage: LlmUsage = Field(default_factory=LlmUsage)
