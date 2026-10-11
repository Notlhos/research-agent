package io.github.notlhos.research.core.api.web.response;

import io.github.notlhos.research.core.persistence.RunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Research run without results")
public record ResearchRunSummaryResponse(
        UUID id,
        String topic,
        RunStatus status,
        Instant createdAt,
        Instant modifiedAt) {
}
