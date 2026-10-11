package io.github.notlhos.research.application.kafka.model;

import java.util.UUID;

/**
 * Report produced by the writer agent, or the error it failed with.
 */
public record ReportResponseMessage(UUID runId, int attempt, ReportDto report, LlmUsageDto usage, String error) {
}
