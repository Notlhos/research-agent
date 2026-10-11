package io.github.notlhos.research.core.api.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to research a topic")
public record CreateResearchRequest(
        @NotBlank
        @Size(min = 3, max = 300)
        @Schema(description = "Research topic", example = "evaluation methods for LLM agents")
        String topic) {
}
