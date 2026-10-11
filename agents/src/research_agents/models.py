from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class SearchPlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    queries: list[str] = Field(min_length=1, max_length=6)


class ReportSection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    heading: str
    body: str


class Report(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str
    summary: str
    sections: list[ReportSection] = Field(min_length=1)
    cited_ids: list[str] = Field(min_length=1)


class TokenUsage(BaseModel):
    llm_calls: int = 0
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_input_tokens: int = 0
    cache_creation_input_tokens: int = 0

    def add(self, usage: Any) -> None:
        self.llm_calls += 1
        self.input_tokens += usage.input_tokens or 0
        self.output_tokens += usage.output_tokens or 0
        self.cache_read_input_tokens += getattr(usage, "cache_read_input_tokens", 0) or 0
        self.cache_creation_input_tokens += getattr(usage, "cache_creation_input_tokens", 0) or 0
