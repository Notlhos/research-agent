package io.github.notlhos.research.application.kafka.model;

import java.util.List;
import java.util.UUID;

public record EmbeddingRequestMessage(
        UUID runId, int attempt, EmbeddingPurpose purpose, List<EmbeddingItemDto> items) {
}
