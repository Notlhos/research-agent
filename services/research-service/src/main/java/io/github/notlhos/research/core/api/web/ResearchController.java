package io.github.notlhos.research.core.api.web;

import io.github.notlhos.research.application.exception.ErrorResponse;
import io.github.notlhos.research.core.api.web.request.CreateResearchRequest;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.ResearchRunSummaryResponse;
import io.github.notlhos.research.core.service.ResearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/research")
@RequiredArgsConstructor
@Tag(name = "Research", description = "Research runs and their reports")
class ResearchController {

    private static final String MARKDOWN_VALUE = "text/markdown";

    private static final MediaType MARKDOWN = MediaType.parseMediaType(MARKDOWN_VALUE + ";charset=UTF-8");

    private final ResearchService researchService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Queue a research run",
            description = "The run starts when admission control grants it a slot; poll it by id until it is DONE.")
    @ApiResponse(responseCode = "202", description = "Run queued")
    @ApiResponse(responseCode = "400", description = "Invalid topic",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResearchRunResponse create(@Valid @RequestBody CreateResearchRequest request) {
        return researchService.create(request);
    }

    @GetMapping
    @Operation(summary = "List research runs", description = "Newest first unless another sort is requested.")
    @ApiResponse(responseCode = "200", description = "Page of runs")
    PagedModel<ResearchRunSummaryResponse> findAll(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(researchService.findAll(pageable));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get a research run with its report and cited sources")
    @ApiResponse(responseCode = "200", description = "Run found")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResearchRunResponse getById(@PathVariable UUID runId) {
        return researchService.getById(runId);
    }

    @GetMapping("/{runId}/report")
    @Operation(summary = "Get the report as Markdown with a reference list")
    @ApiResponse(responseCode = "200", description = "Report, or the run status while it is not DONE",
            content = @Content(mediaType = MARKDOWN_VALUE, schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<String> getReport(@PathVariable UUID runId) {
        return ResponseEntity.ok().contentType(MARKDOWN).body(researchService.renderReport(runId));
    }

    @PostMapping("/{runId}/cancel")
    @Operation(summary = "Cancel a run that has not finished")
    @ApiResponse(responseCode = "200", description = "Run cancelled")
    @ApiResponse(responseCode = "404", description = "Run not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Run has already finished",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResearchRunResponse cancel(@PathVariable UUID runId) {
        return researchService.cancel(runId);
    }

}
