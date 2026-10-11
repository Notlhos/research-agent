import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "agents" / "src"))

from research_agents.__main__ import build_llm
from research_agents.config import Settings
from research_agents.contracts import Source, WriteRequest
from research_agents.handlers import WriteHandler
from research_agents.tracing import NoopTracer

FIXTURES = Path(__file__).resolve().parent / "fixtures"


def call_api(prompt, options, context):
    fixture = json.loads((FIXTURES / context["vars"]["fixture"]).read_text(encoding="utf-8"))
    settings = Settings()
    handler = WriteHandler(build_llm(settings, NoopTracer()), settings.max_correction_attempts)
    request = WriteRequest(
        run_id="eval",
        attempt=1,
        topic=fixture["topic"],
        sources=[Source.model_validate(source) for source in fixture["sources"]],
    )
    try:
        response = handler.process(request)
    except Exception as error:
        return {"error": f"{type(error).__name__}: {error}"}
    usage = response.usage
    return {
        "output": response.report.model_dump_json(by_alias=True),
        "tokenUsage": {
            "prompt": usage.input_tokens,
            "completion": usage.output_tokens,
            "total": usage.input_tokens + usage.output_tokens,
        },
        "metadata": {"llmCalls": usage.llm_calls, "provider": settings.llm_provider.value},
    }
