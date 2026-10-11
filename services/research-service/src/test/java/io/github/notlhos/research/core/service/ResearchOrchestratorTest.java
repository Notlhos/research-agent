package io.github.notlhos.research.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.application.exception.ConflictException;
import io.github.notlhos.research.application.exception.NotFoundException;
import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import io.github.notlhos.research.application.integration.milvus.PaperVectorStore;
import io.github.notlhos.research.application.integration.milvus.model.ScoredPaper;
import io.github.notlhos.research.application.kafka.model.EmbeddingPurpose;
import io.github.notlhos.research.application.kafka.model.EmbeddingResponseMessage;
import io.github.notlhos.research.application.kafka.model.EmbeddingVectorDto;
import io.github.notlhos.research.application.kafka.model.LlmUsageDto;
import io.github.notlhos.research.application.kafka.model.PlanResponseMessage;
import io.github.notlhos.research.application.kafka.model.ReportDto;
import io.github.notlhos.research.application.kafka.model.ReportResponseMessage;
import io.github.notlhos.research.application.kafka.model.ReportSectionDto;
import io.github.notlhos.research.core.mapper.PaperMapper;
import io.github.notlhos.research.core.persistence.PaperRepository;
import io.github.notlhos.research.core.persistence.ResearchRunEntity;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ResearchOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private static final LlmUsageDto USAGE = new LlmUsageDto(1, 100, 50, 0, 0);

    private static final ResearchProperties PROPERTIES = new ResearchProperties(4, 2, Duration.ofMinutes(5), 8, 12,
            Duration.ofSeconds(2), Duration.ofSeconds(15), null, null, null);

    @Mock
    private ResearchRunRepository runRepository;

    @Mock
    private PaperRepository paperRepository;

    @Mock
    private PaperVectorStore vectorStore;

    @Mock
    private StepDispatcher dispatcher;

    @Mock
    private ResearchMetrics metrics;

    private ResearchOrchestrator orchestrator;

    private ResearchRunEntity run;

    @BeforeEach
    void setUp() {
        orchestrator = new ResearchOrchestrator(runRepository, paperRepository, vectorStore, dispatcher,
                Mappers.getMapper(PaperMapper.class), PROPERTIES, metrics, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        run = ResearchRunEntity.create("llm agents");
        lenient().when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
    }

    @Test
    void createStoresPendingRunWithStrippedTopic() {
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResearchRunEntity created = orchestrator.create("  tool use  ");

        assertThat(created.getStatus()).isEqualTo(RunStatus.PENDING);
        assertThat(created.getTopic()).isEqualTo("tool use");
    }

    @Test
    void startMovesPendingRunToPlanning() {
        orchestrator.start(run.getId());

        assertThat(run.getStatus()).isEqualTo(RunStatus.PLANNING);
        assertDispatched(RunStatus.PLANNING, 1);
    }

    @Test
    void planResponseRecordsQueriesAndStartsSearch() {
        run.enter(RunStatus.PLANNING, NOW);

        orchestrator.onPlanned(new PlanResponseMessage(run.getId(), 1, List.of("agents", "tools"), USAGE, null));

        assertThat(run.getStatus()).isEqualTo(RunStatus.SEARCHING);
        assertThat(run.getQueries()).containsExactly("agents", "tools");
        assertThat(run.getUsage().getInputTokens()).isEqualTo(100);
        assertDispatched(RunStatus.SEARCHING, 2);
    }

    @Test
    void staleAttemptIsIgnored() {
        run.enter(RunStatus.PLANNING, NOW);
        run.retry("timeout", NOW);

        orchestrator.onPlanned(new PlanResponseMessage(run.getId(), 1, List.of("late"), USAGE, null));

        assertThat(run.getStatus()).isEqualTo(RunStatus.PLANNING);
        assertThat(run.getQueries()).isEmpty();
        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void requeuedRunIgnoresResponsesToItsPreviousExecution() {
        run.enter(RunStatus.PLANNING, NOW);
        run.finish(RunStatus.FAILED, "planner unavailable");
        orchestrator.retry(run.getId());
        orchestrator.start(run.getId());

        orchestrator.onPlanned(new PlanResponseMessage(run.getId(), 1, List.of("late"), USAGE, null));

        assertThat(run.getStatus()).isEqualTo(RunStatus.PLANNING);
        assertThat(run.getQueries()).isEmpty();
        assertDispatched(RunStatus.PLANNING, 2);
    }

    @Test
    void failedStepIsRetriedThenFailsTheRun() {
        run.enter(RunStatus.PLANNING, NOW);

        orchestrator.onPlanned(new PlanResponseMessage(run.getId(), 1, List.of(), USAGE, "RateLimitError"));
        assertThat(run.getAttempt()).isEqualTo(2);
        assertDispatched(RunStatus.PLANNING, 2);

        orchestrator.onPlanned(new PlanResponseMessage(run.getId(), 2, List.of(), USAGE, "RateLimitError"));
        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getError()).isEqualTo("RateLimitError");
    }

    @Test
    void searchResultsAreStoredAndSentForIndexing() {
        run.enter(RunStatus.SEARCHING, NOW);
        ArxivPaper paper = new ArxivPaper("2401.00001", "T", "S", List.of("A"), "2024-01-01", "u");

        orchestrator.onSearchFinished(SearchFinished.success(run.getId(), 1, List.of(paper)));

        assertThat(run.getStatus()).isEqualTo(RunStatus.INDEXING);
        assertThat(run.getFoundPaperIds()).containsExactly("2401.00001");
        verify(paperRepository).saveAll(argThat(papers -> papers.iterator().next().getArxivId().equals("2401.00001")));
    }

    @Test
    void emptySearchFailsTheRun() {
        run.enter(RunStatus.SEARCHING, NOW);

        orchestrator.onSearchFinished(SearchFinished.success(run.getId(), 1, List.of()));

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void topicEmbeddingRetrievesSourcesForTheWriter() {
        run.enter(RunStatus.RETRIEVING, NOW);
        when(vectorStore.search(any(), any(), anyInt())).thenReturn(List.of(new ScoredPaper("2401.00002", 0.9f)));

        orchestrator.onEmbedded(new EmbeddingResponseMessage(run.getId(), 1, EmbeddingPurpose.QUERY,
                List.of(new EmbeddingVectorDto("topic", new float[] {0.1f, 0.2f})), null));

        assertThat(run.getStatus()).isEqualTo(RunStatus.WRITING);
        assertThat(run.getSourcePaperIds()).containsExactly("2401.00002");
    }

    @Test
    void retrievalIsRestrictedToPapersFoundByTheRun() {
        run.enter(RunStatus.SEARCHING, NOW);
        run.recordFoundPapers(List.of("2401.00001", "2401.00002"));
        run.enter(RunStatus.RETRIEVING, NOW);
        when(vectorStore.search(any(), any(), anyInt())).thenReturn(List.of(new ScoredPaper("2401.00002", 0.9f)));

        orchestrator.onEmbedded(new EmbeddingResponseMessage(run.getId(), run.getAttempt(), EmbeddingPurpose.QUERY,
                List.of(new EmbeddingVectorDto("topic", new float[] {0.1f})), null));

        verify(vectorStore).search(any(), eq(List.of("2401.00001", "2401.00002")), eq(12));
    }

    @Test
    void reportCompletesTheRun() {
        run.enter(RunStatus.WRITING, NOW);
        ReportDto report = new ReportDto("T", "S [2401.00002].", List.of(new ReportSectionDto("H", "B")),
                List.of("2401.00002"));

        orchestrator.onReportWritten(new ReportResponseMessage(run.getId(), 1, report, USAGE, null));

        assertThat(run.getStatus()).isEqualTo(RunStatus.DONE);
        assertThat(run.getReportJson()).contains("2401.00002");
        verify(metrics).runFinished(RunStatus.DONE);
    }

    @Test
    void cancelledRunIgnoresLateResponses() {
        run.enter(RunStatus.WRITING, NOW);
        orchestrator.cancel(run.getId());

        orchestrator.onReportWritten(new ReportResponseMessage(run.getId(), 1, null, USAGE, null));

        assertThat(run.getStatus()).isEqualTo(RunStatus.CANCELLED);
    }

    @Test
    void finishedRunCannotBeCancelled() {
        run.finish(RunStatus.DONE, null);

        assertThatThrownBy(() -> orchestrator.cancel(run.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void retryReturnsFailedRunToTheQueue() {
        run.enter(RunStatus.WRITING, NOW);
        run.recordSources(List.of("2401.00002"));
        run.finish(RunStatus.FAILED, "Writer returned no report");

        orchestrator.retry(run.getId());

        assertThat(run.getStatus()).isEqualTo(RunStatus.PENDING);
        assertThat(run.getError()).isNull();
        assertThat(run.getSourcePaperIds()).isEmpty();
    }

    @Test
    void onlyFailedRunsCanBeRetried() {
        run.enter(RunStatus.WRITING, NOW);

        assertThatThrownBy(() -> orchestrator.retry(run.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownRunIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> orchestrator.cancel(unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void stuckStepIsRetried() {
        run.enter(RunStatus.WRITING, NOW.minus(Duration.ofMinutes(10)));

        orchestrator.recoverIfStuck(run.getId());

        assertThat(run.getAttempt()).isEqualTo(2);
        assertThat(run.getError()).contains("timed out");
    }

    private void assertDispatched(RunStatus step, int attempt) {
        ArgumentCaptor<RunSnapshot> snapshot = ArgumentCaptor.forClass(RunSnapshot.class);
        verify(dispatcher, atLeastOnce()).dispatchAfterCommit(snapshot.capture());
        assertThat(snapshot.getValue().status()).isEqualTo(step);
        assertThat(snapshot.getValue().attempt()).isEqualTo(attempt);
    }

}
