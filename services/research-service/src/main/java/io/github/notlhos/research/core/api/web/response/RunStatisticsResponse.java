package io.github.notlhos.research.core.api.web.response;

import io.github.notlhos.research.core.persistence.RunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Run counts and LLM usage over all runs")
public record RunStatisticsResponse(Map<RunStatus, Long> runsByStatus, TokenUsageResponse totalUsage) {
}
