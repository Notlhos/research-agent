from prometheus_client import Counter, Histogram

REQUESTS = Counter("research_agent_requests_total", "Agent requests processed", ["role", "outcome"])
LATENCY = Histogram(
    "research_agent_request_seconds",
    "Agent request processing time",
    ["role"],
    buckets=(0.1, 0.5, 1, 2, 5, 10, 30, 60, 120, 300),
)
TOKENS = Counter("research_agent_llm_tokens_total", "LLM tokens used by agents", ["role", "kind"])


class RoleMetrics:
    def __init__(self, role: str) -> None:
        self._role = role

    def observe(self, outcome: str, seconds: float, usage: object | None) -> None:
        REQUESTS.labels(self._role, outcome).inc()
        LATENCY.labels(self._role).observe(seconds)
        if usage is None:
            return
        TOKENS.labels(self._role, "input").inc(usage.input_tokens)
        TOKENS.labels(self._role, "output").inc(usage.output_tokens)
        TOKENS.labels(self._role, "cache_read").inc(usage.cache_read_input_tokens)
