package io.github.notlhos.research.core.api.web;

import io.github.notlhos.research.application.exception.ErrorResponse;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.RunStatisticsResponse;
import io.github.notlhos.research.core.service.ResearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admins/research")
@RequiredArgsConstructor
@Tag(name = "Research administration", description = "Operations that require the admin API key")
class AdminController {

    private final ResearchService researchService;

    @GetMapping("/statistics")
    @Operation(summary = "Run counts per status and LLM usage of all runs")
    @ApiResponse(responseCode = "200", description = "Statistics")
    RunStatisticsResponse statistics() {
        return researchService.statistics();
    }

    @PostMapping("/{runId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Retry a failed run",
            description = "Returns the run to the admission queue; it is executed again from planning.")
    @ApiResponse(responseCode = "202", description = "Run queued again")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Run is not failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResearchRunResponse retry(@PathVariable UUID runId) {
        return researchService.retry(runId);
    }

}
