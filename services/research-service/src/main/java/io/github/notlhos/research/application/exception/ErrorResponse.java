package io.github.notlhos.research.application.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.HttpStatus;

@Schema(description = "Error returned by every endpoint")
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "404") int status,
        @Schema(description = "Human-readable description", example = "Research run not found") String message,
        @Schema(description = "Error codes, or field violations for validation errors",
                example = "[\"RESEARCH_RUN_NOT_FOUND\"]") List<String> codes) {

    public static ErrorResponse of(HttpStatus status, String message, List<String> codes) {
        return new ErrorResponse(status.value(), message, List.copyOf(codes));
    }

}
