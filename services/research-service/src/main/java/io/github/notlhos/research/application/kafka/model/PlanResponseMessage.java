package io.github.notlhos.research.application.kafka.model;

import java.util.List;
import java.util.UUID;

/**
 * Search queries produced by the planner agent, or the error it failed with.
 */
public record PlanResponseMessage(UUID runId, int attempt, List<String> queries, LlmUsageDto usage, String error) {
}
