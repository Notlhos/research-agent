package io.github.notlhos.research.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.notlhos.research.core.api.web.response.ReportResponse;
import io.github.notlhos.research.core.api.web.response.ReportSectionResponse;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.SourceResponse;
import io.github.notlhos.research.core.api.web.response.TokenUsageResponse;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportRendererTest {

    private final ReportRenderer renderer = new ReportRenderer();

    @Test
    void rendersSectionsAndReferences() {
        ReportResponse report = new ReportResponse("Agents", "Summary [2401.00001].",
                List.of(new ReportSectionResponse("Findings", "Body [2401.00001].")), List.of("2401.00001"));
        SourceResponse source = new SourceResponse("2401.00001", "Paper", List.of("A", "B", "C", "D"),
                "2024-01-01", "https://arxiv.org/abs/2401.00001");

        String markdown = renderer.markdown(run(RunStatus.DONE, report, List.of(source)));

        assertThat(markdown).startsWith("# Agents")
                .contains("## Findings")
                .contains("- [2401.00001] Paper. A, B, C et al. (2024). https://arxiv.org/abs/2401.00001");
    }

    @Test
    void rendersStatusWhileReportIsMissing() {
        assertThat(renderer.markdown(run(RunStatus.WRITING, null, List.of()))).contains("Status: WRITING");
    }

    private static ResearchRunResponse run(RunStatus status, ReportResponse report, List<SourceResponse> sources) {
        Instant now = Instant.now();
        return new ResearchRunResponse(UUID.randomUUID(), "agents", status, 1, List.of(), 0, report, sources,
                new TokenUsageResponse(0, 0, 0, 0, 0), null, now, "api-client", now);
    }

}
