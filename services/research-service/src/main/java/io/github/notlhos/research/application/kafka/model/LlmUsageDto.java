package io.github.notlhos.research.application.kafka.model;

public record LlmUsageDto(
        int llmCalls,
        long inputTokens,
        long outputTokens,
        long cacheReadInputTokens,
        long cacheCreationInputTokens) {
}
