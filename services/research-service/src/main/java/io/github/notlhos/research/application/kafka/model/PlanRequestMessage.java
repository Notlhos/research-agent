package io.github.notlhos.research.application.kafka.model;

import java.util.UUID;

public record PlanRequestMessage(UUID runId, int attempt, String topic) {
}
