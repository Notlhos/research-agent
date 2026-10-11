package io.github.notlhos.research.application.kafka.model;

import java.util.List;
import java.util.UUID;

public record ReportRequestMessage(UUID runId, int attempt, String topic, List<SourceDocumentDto> sources) {
}
