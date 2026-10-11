package io.github.notlhos.research.core.api.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Paper cited in the report")
public record SourceResponse(
        @Schema(example = "2401.01234") String arxivId,
        @Schema(example = "Self-Correcting Agents for Tool Use") String title,
        List<String> authors,
        @Schema(description = "Publication date", example = "2024-01-03") String published,
        @Schema(example = "https://arxiv.org/abs/2401.01234") String url) {
}
