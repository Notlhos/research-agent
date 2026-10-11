package io.github.notlhos.research.core.api.web.response;

import io.github.notlhos.research.core.persistence.RunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Research run with its report and cited sources")
public record ResearchRunResponse(
        UUID id,
        @Schema(example = "evaluation methods for LLM agents") String topic,
        RunStatus status,
        @Schema(description = "Attempt of the current step") int attempt,
        @Schema(description = "arXiv queries written by the planner") List<String> queries,
        @Schema(description = "Papers returned by the search") int papersFound,
        @Schema(description = "Present once the run is DONE") ReportResponse report,
        @Schema(description = "Papers cited in the report") List<SourceResponse> sources,
        TokenUsageResponse usage,
        @Schema(description = "Last error, if any") String error,
        Instant createdAt,
        @Schema(example = "api-client") String createdBy,
        Instant modifiedAt) {
}
