package io.github.notlhos.research.application.integration.milvus.model;

/**
 * Paper found by a similarity search, with its cosine similarity to the query.
 */
public record ScoredPaper(String arxivId, float score) {
}
