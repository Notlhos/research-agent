package io.github.notlhos.research.core.persistence;

public record TokenTotals(
        long llmCalls,
        long inputTokens,
        long outputTokens,
        long cacheReadInputTokens,
        long cacheCreationInputTokens) {
}
