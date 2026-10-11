package io.github.notlhos.research.core.api.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Report section with inline citations such as [2401.01234]")
public record ReportSectionResponse(String heading, String body) {
}
