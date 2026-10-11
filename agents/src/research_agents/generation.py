from collections.abc import Callable

from pydantic import BaseModel, ValidationError

from research_agents.llm import LlmOutputError
from research_agents.models import TokenUsage
from research_agents.ports import JsonLlm, JsonRequest


def no_problems(_: BaseModel) -> list[str]:
    return []


def generate[T: BaseModel](
    llm: JsonLlm,
    request: JsonRequest,
    output_type: type[T],
    usage: TokenUsage,
    attempts: int,
    check: Callable[[T], list[str]] = no_problems,
) -> T:
    """Generates a validated object, feeding validation problems back to the model on failure.

    Structured outputs guarantee syntactically valid JSON, but not domain rules such as "cite only
    the provided sources". Returning the concrete problems as a follow-up turn, instead of re-sending
    the request blindly, gives the retry a precise target and keeps the original prompt prefix cacheable.
    """
    messages: list[dict] = [{"role": "user", "content": request.prompt}]
    problems: list[str] = []
    for _ in range(attempts):
        text = llm.complete(request, messages, usage)
        result, problems = _validate(text, output_type, check)
        if result is not None:
            return result
        messages += [
            {"role": "assistant", "content": text},
            {"role": "user", "content": _correction_prompt(problems)},
        ]
    raise LlmOutputError(f"Output still invalid after {attempts} attempts: {'; '.join(problems)}")


def _validate[T: BaseModel](
    text: str, output_type: type[T], check: Callable[[T], list[str]]
) -> tuple[T | None, list[str]]:
    try:
        result = output_type.model_validate_json(text)
    except ValidationError as error:
        return None, [str(error)]
    problems = check(result)
    return (None, problems) if problems else (result, [])


def _correction_prompt(problems: list[str]) -> str:
    listed = "\n".join(f"- {problem}" for problem in problems)
    return f"Your previous answer has these problems:\n{listed}\n\nReturn the full corrected answer."
