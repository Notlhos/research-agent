package io.github.notlhos.research.application.kafka.model;

/**
 * Text to embed, keyed by the id its vector is returned under.
 */
public record EmbeddingItemDto(String id, String text) {
}
