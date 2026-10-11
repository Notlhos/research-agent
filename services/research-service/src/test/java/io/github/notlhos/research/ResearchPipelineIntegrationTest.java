package io.github.notlhos.research;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.notlhos.research.application.integration.arxiv.ArxivClient;
import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import io.github.notlhos.research.application.integration.milvus.PaperVectorStore;
import io.github.notlhos.research.application.integration.milvus.model.ScoredPaper;
import io.github.notlhos.research.application.kafka.KafkaTopics;
import io.github.notlhos.research.application.security.SecurityConfig;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FakeAgents.class})
class ResearchPipelineIntegrationTest {

    private static final String CLIENT_KEY = "test-api-key";

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final ArxivPaper FIRST = new ArxivPaper("2401.00001", "Tool use", "Agents call tools.",
            List.of("Ada Lovelace"), "2024-01-01", "https://arxiv.org/abs/2401.00001");

    private static final ArxivPaper SECOND = new ArxivPaper("2401.00002", "Agent evaluation", "Judges score agents.",
            List.of("Alan Turing"), "2024-01-02", "https://arxiv.org/abs/2401.00002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ResearchRunRepository runRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private FakeAgents fakeAgents;

    @MockitoBean
    private ArxivClient arxivClient;

    @MockitoBean
    private PaperVectorStore vectorStore;

    @Test
    void runCompletesThroughAgentsOverKafka() throws Exception {
        when(arxivClient.search(anyString(), anyInt())).thenReturn(List.of(FIRST, SECOND));
        when(vectorStore.search(any(), any(), anyInt())).thenReturn(List.of(
                new ScoredPaper(SECOND.arxivId(), 0.9f), new ScoredPaper(FIRST.arxivId(), 0.8f)));

        UUID runId = createRun("tool use in llm agents");
        await().atMost(TIMEOUT).until(() -> runRepository.findById(runId).orElseThrow().getStatus().isTerminal());

        mockMvc.perform(get("/research/{runId}", runId).header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(RunStatus.DONE.name()))
                .andExpect(jsonPath("$.createdBy").value("api-client"))
                .andExpect(jsonPath("$.papersFound").value(2))
                .andExpect(jsonPath("$.sources[0].arxivId").value(SECOND.arxivId()))
                .andExpect(jsonPath("$.report.citedIds[0]").value(SECOND.arxivId()))
                .andExpect(jsonPath("$.usage.llmCalls").value(2));
        verify(vectorStore).upsert(argThat(vectors -> vectors.size() == 2));
    }

    @Test
    void unreadableAgentResponseGoesToDeadLetterTopic() {
        kafkaTemplate.send(KafkaTopics.PLAN_RESPONSE, "broken", "not json");

        await().atMost(TIMEOUT).until(() -> fakeAgents.deadLetters().contains("not json"));
    }

    /* ***** Private Methods ***** */

    private UUID createRun(String topic) throws Exception {
        String body = mockMvc.perform(post("/research")
                        .header(SecurityConfig.API_KEY_HEADER, CLIENT_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"" + topic + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonMapper.readValue(body, ResearchRunResponse.class).id();
    }

}
