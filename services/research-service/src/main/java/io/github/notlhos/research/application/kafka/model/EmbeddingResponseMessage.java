package io.github.notlhos.research.application.kafka.model;

import java.util.List;
import java.util.UUID;

/**
 * Vectors produced by the embedder agent, or the error it failed with.
 */
public record EmbeddingResponseMessage(
        UUID runId, int attempt, EmbeddingPurpose purpose, List<EmbeddingVectorDto> vectors, String error) {
}
