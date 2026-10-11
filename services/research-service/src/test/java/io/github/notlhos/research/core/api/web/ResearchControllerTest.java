package io.github.notlhos.research.core.api.web;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.notlhos.research.application.exception.ConflictException;
import io.github.notlhos.research.application.exception.ErrorCode;
import io.github.notlhos.research.application.exception.NotFoundException;
import io.github.notlhos.research.application.security.SecurityConfig;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.RunStatisticsResponse;
import io.github.notlhos.research.core.api.web.response.TokenUsageResponse;
import io.github.notlhos.research.core.persistence.RunStatus;
import io.github.notlhos.research.core.service.ResearchService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@Import(SecurityConfig.class)
@WebMvcTest(controllers = {ResearchController.class, AdminController.class})
class ResearchControllerTest {

    private static final String CLIENT_KEY = "test-api-key";

    private static final String ADMIN_KEY = "test-admin-key";

    private static final UUID RUN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResearchService researchService;

    @Test
    void clientKeyCreatesRun() throws Exception {
        when(researchService.create(any())).thenReturn(run(RunStatus.PENDING));

        mockMvc.perform(post("/research")
                        .header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"tool use in llm agents\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/research/{runId}", RUN_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void unknownApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/research/{runId}", RUN_ID).header(SecurityConfig.API_KEY_HEADER, "guess"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientKeyCannotCallAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admins/research/statistics").header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.FORBIDDEN));
    }

    @Test
    void adminKeyReadsStatistics() throws Exception {
        when(researchService.statistics()).thenReturn(new RunStatisticsResponse(
                Map.of(RunStatus.DONE, 3L), new TokenUsageResponse(6, 3_000, 900, 0, 0)));

        mockMvc.perform(get("/admins/research/statistics").header(SecurityConfig.API_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runsByStatus.DONE").value(3))
                .andExpect(jsonPath("$.totalUsage.inputTokens").value(3_000));
    }

    @Test
    void adminKeyGrantsClientEndpoints() throws Exception {
        when(researchService.getById(RUN_ID)).thenReturn(run(RunStatus.DONE));

        mockMvc.perform(get("/research/{runId}", RUN_ID).header(SecurityConfig.API_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void invalidTopicReturnsFieldViolations() throws Exception {
        mockMvc.perform(post("/research")
                        .header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Validation failed: topic size")))
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.VALIDATION_FAILED))
                .andExpect(jsonPath("$.codes[1]").value("topic.Size"));
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/research")
                        .header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.MALFORMED_REQUEST));
    }

    @Test
    void malformedRunIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/research/not-a-uuid").header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.MALFORMED_REQUEST));
    }

    @Test
    void unknownRunIsNotFound() throws Exception {
        when(researchService.getById(RUN_ID))
                .thenThrow(new NotFoundException(ErrorCode.RUN_NOT_FOUND, "Research run not found"));

        mockMvc.perform(get("/research/{runId}", RUN_ID).header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.RUN_NOT_FOUND));
    }

    @Test
    void cancellingFinishedRunIsConflict() throws Exception {
        when(researchService.cancel(RUN_ID))
                .thenThrow(new ConflictException(ErrorCode.RUN_NOT_CANCELLABLE, "Research run is already DONE"));

        mockMvc.perform(post("/research/{runId}/cancel", RUN_ID).header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codes[0]").value(ErrorCode.RUN_NOT_CANCELLABLE));
    }

    @Test
    void reportIsServedAsMarkdown() throws Exception {
        when(researchService.renderReport(RUN_ID)).thenReturn("# Report\n");

        mockMvc.perform(get("/research/{runId}/report", RUN_ID).header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"));
    }

    private static ResearchRunResponse run(RunStatus status) {
        Instant now = Instant.now();
        return new ResearchRunResponse(RUN_ID, "tool use in llm agents", status, 0, List.of(), 0, null, List.of(),
                new TokenUsageResponse(0, 0, 0, 0, 0), null, now, "api-client", now);
    }

}
