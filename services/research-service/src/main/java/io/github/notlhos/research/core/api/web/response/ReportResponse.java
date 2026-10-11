package io.github.notlhos.research.core.api.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Report of a finished run")
public record ReportResponse(
        String title,
        String summary,
        List<ReportSectionResponse> sections,
        @Schema(description = "arXiv ids cited in the report") List<String> citedIds) {
}
