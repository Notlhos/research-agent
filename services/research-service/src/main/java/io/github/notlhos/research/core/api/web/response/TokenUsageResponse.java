package io.github.notlhos.research.core.api.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "LLM calls and tokens spent")
public record TokenUsageResponse(
        long llmCalls,
        long inputTokens,
        long outputTokens,
        long cacheReadInputTokens,
        long cacheCreationInputTokens) {
}
