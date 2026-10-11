package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.application.exception.ConflictException;
import io.github.notlhos.research.application.exception.ErrorCode;
import io.github.notlhos.research.application.exception.NotFoundException;
import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import io.github.notlhos.research.application.integration.milvus.PaperVectorStore;
import io.github.notlhos.research.application.integration.milvus.model.ScoredPaper;
import io.github.notlhos.research.application.kafka.model.EmbeddingPurpose;
import io.github.notlhos.research.application.kafka.model.EmbeddingResponseMessage;
import io.github.notlhos.research.application.kafka.model.EmbeddingVectorDto;
import io.github.notlhos.research.application.kafka.model.LlmUsageDto;
import io.github.notlhos.research.application.kafka.model.PlanResponseMessage;
import io.github.notlhos.research.application.kafka.model.ReportResponseMessage;
import io.github.notlhos.research.core.mapper.PaperMapper;
import io.github.notlhos.research.core.persistence.PaperEntity;
import io.github.notlhos.research.core.persistence.PaperRepository;
import io.github.notlhos.research.core.persistence.ResearchRunEntity;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives a research run through {@code PLANNING -> SEARCHING -> INDEXING -> RETRIEVING -> WRITING -> DONE}.
 *
 * <p>Every step is asynchronous: the orchestrator persists the new state, dispatches the work and returns; the
 * step ends when a matching response (same run id, step and attempt) arrives. Anything else, such as a response
 * for a cancelled run, a late answer from a timed-out attempt or a Kafka redelivery, is ignored, so agents can
 * be delivered at least once without corrupting a run. Failed steps are retried with a new attempt number up to
 * {@code research.max-step-attempts}; the run then fails with the last error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchOrchestrator {

    private final ResearchRunRepository runRepository;

    private final PaperRepository paperRepository;

    private final PaperVectorStore vectorStore;

    private final StepDispatcher dispatcher;

    private final PaperMapper paperMapper;

    private final ResearchProperties properties;

    private final ResearchMetrics metrics;

    private final JsonMapper jsonMapper;

    private final Clock clock;

    @Transactional
    public ResearchRunEntity create(String topic) {
        return runRepository.save(ResearchRunEntity.create(topic.strip()));
    }

    /**
     * Starts planning for a pending run; runs in any other status are left unchanged.
     */
    @Transactional
    public void start(UUID runId) {
        runRepository.findById(runId)
                .filter(run -> run.getStatus() == RunStatus.PENDING)
                .ifPresent(run -> enter(run, RunStatus.PLANNING));
    }

    @Transactional
    public void onPlanned(PlanResponseMessage response) {
        awaiting(response.runId(), RunStatus.PLANNING, response.attempt()).ifPresent(run -> {
            recordUsage(run, response.usage());
            if (response.error() != null) {
                retryOrFail(run, response.error());
                return;
            }
            run.recordQueries(response.queries());
            enter(run, RunStatus.SEARCHING);
        });
    }

    @EventListener
    @Transactional
    public void onSearchFinished(SearchFinished result) {
        awaiting(result.runId(), RunStatus.SEARCHING, result.attempt()).ifPresent(run -> {
            if (result.error() != null) {
                retryOrFail(run, result.error());
                return;
            }
            if (result.papers().isEmpty()) {
                finish(run, RunStatus.FAILED, "arXiv returned no papers for queries " + run.getQueries());
                return;
            }
            storeNewPapers(result.papers());
            run.recordFoundPapers(result.papers().stream().map(ArxivPaper::arxivId).toList());
            enter(run, RunStatus.INDEXING);
        });
    }

    @Transactional
    public void onEmbedded(EmbeddingResponseMessage response) {
        RunStatus step = response.purpose() == EmbeddingPurpose.DOCUMENTS ? RunStatus.INDEXING : RunStatus.RETRIEVING;
        awaiting(response.runId(), step, response.attempt()).ifPresent(run -> {
            if (response.error() != null) {
                retryOrFail(run, response.error());
            } else if (step == RunStatus.INDEXING) {
                vectorStore.upsert(response.vectors());
                enter(run, RunStatus.RETRIEVING);
            } else {
                retrieveSources(run, response.vectors());
            }
        });
    }

    @Transactional
    public void onReportWritten(ReportResponseMessage response) {
        awaiting(response.runId(), RunStatus.WRITING, response.attempt()).ifPresent(run -> {
            recordUsage(run, response.usage());
            if (response.error() != null || response.report() == null) {
                retryOrFail(run, response.error() == null ? "Writer returned no report" : response.error());
                return;
            }
            run.recordReport(jsonMapper.writeValueAsString(response.report()));
            finish(run, RunStatus.DONE, null);
        });
    }

    @Transactional
    public void cancel(UUID runId) {
        ResearchRunEntity run = findRun(runId);
        if (run.getStatus().isTerminal()) {
            throw new ConflictException(ErrorCode.RUN_NOT_CANCELLABLE,
                    "Research run " + runId + " is already " + run.getStatus());
        }
        finish(run, RunStatus.CANCELLED, "Cancelled by client");
    }

    @Transactional
    public void retry(UUID runId) {
        ResearchRunEntity run = findRun(runId);
        if (run.getStatus() != RunStatus.FAILED) {
            throw new ConflictException(ErrorCode.RUN_NOT_RETRYABLE,
                    "Only failed runs can be retried, research run " + runId + " is " + run.getStatus());
        }
        run.requeue();
        log.info("Run {} returned to the admission queue", runId);
    }

    /**
     * Retries or fails the current step of a run that has waited longer than {@code research.step-timeout}.
     */
    @Transactional
    public void recoverIfStuck(UUID runId) {
        Instant deadline = clock.instant().minus(properties.stepTimeout());
        runRepository.findById(runId)
                .filter(run -> RunStatus.ACTIVE.contains(run.getStatus()))
                .filter(run -> run.getStepStartedAt() != null && run.getStepStartedAt().isBefore(deadline))
                .ifPresent(run -> retryOrFail(run, run.getStatus() + " timed out after " + properties.stepTimeout()));
    }

    /* ***** Private Methods ***** */

    private ResearchRunEntity findRun(UUID runId) {
        return runRepository.findById(runId).orElseThrow(() -> new NotFoundException(ErrorCode.RUN_NOT_FOUND,
                "Research run " + runId + " not found"));
    }

    private void storeNewPapers(List<ArxivPaper> papers) {
        Set<String> stored = paperRepository.findAllById(papers.stream().map(ArxivPaper::arxivId).toList()).stream()
                .map(PaperEntity::getArxivId)
                .collect(Collectors.toSet());
        paperRepository.saveAll(papers.stream()
                .filter(paper -> !stored.contains(paper.arxivId()))
                .map(paperMapper::toEntity)
                .toList());
    }

    private void retrieveSources(ResearchRunEntity run, List<EmbeddingVectorDto> topicVectors) {
        if (topicVectors.isEmpty()) {
            retryOrFail(run, "Embedder returned no topic vector");
            return;
        }
        List<String> sources = vectorStore.search(topicVectors.getFirst().vector(), run.getFoundPaperIds(),
                        properties.retrievalTopK())
                .stream()
                .map(ScoredPaper::arxivId)
                .toList();
        run.recordSources(sources);
        enter(run, RunStatus.WRITING);
    }

    private Optional<ResearchRunEntity> awaiting(UUID runId, RunStatus step, int attempt) {
        Optional<ResearchRunEntity> run = runRepository.findById(runId)
                .filter(candidate -> candidate.awaits(step, attempt));
        if (run.isEmpty()) {
            log.info("Ignoring stale {} result for run {} attempt {}", step, runId, attempt);
        }
        return run;
    }

    private void enter(ResearchRunEntity run, RunStatus step) {
        completeCurrentStep(run);
        run.enter(step, clock.instant());
        dispatcher.dispatchAfterCommit(RunSnapshot.of(run));
    }

    private void retryOrFail(ResearchRunEntity run, String reason) {
        if (run.getStepAttempt() >= properties.maxStepAttempts()) {
            finish(run, RunStatus.FAILED, reason);
            return;
        }
        log.warn("Retrying {} for run {} after: {}", run.getStatus(), run.getId(), reason);
        metrics.stepRetried(run.getStatus());
        run.retry(reason, clock.instant());
        dispatcher.dispatchAfterCommit(RunSnapshot.of(run));
    }

    private void finish(ResearchRunEntity run, RunStatus terminal, String reason) {
        completeCurrentStep(run);
        run.finish(terminal, reason);
        metrics.runFinished(terminal);
        log.info("Run {} finished as {}", run.getId(), terminal);
    }

    private void completeCurrentStep(ResearchRunEntity run) {
        if (run.getStepStartedAt() != null && RunStatus.ACTIVE.contains(run.getStatus())) {
            metrics.stepCompleted(run.getStatus(), Duration.between(run.getStepStartedAt(), clock.instant()));
        }
    }

    private void recordUsage(ResearchRunEntity run, LlmUsageDto usage) {
        run.getUsage().add(usage);
        metrics.tokensUsed(usage);
    }

}
