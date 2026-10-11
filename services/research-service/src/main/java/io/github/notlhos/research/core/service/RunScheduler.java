package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Admission and recovery loops. Admission counts active runs and then starts pending ones without a database
 * lock, which is correct only with a single service replica; the Helm chart refuses more than one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RunScheduler {

    private final ResearchRunRepository runRepository;

    private final ResearchOrchestrator orchestrator;

    private final ResearchProperties properties;

    private final Clock clock;

    @Scheduled(fixedDelayString = "${research.admission-interval}")
    void admitPendingRuns() {
        int free = properties.maxActiveRuns() - (int) runRepository.countByStatusIn(RunStatus.ACTIVE);
        if (free <= 0) {
            return;
        }
        runRepository.findIdsByStatus(RunStatus.PENDING, PageRequest.of(0, free))
                .forEach(runId -> guarded("start", runId, () -> orchestrator.start(runId)));
    }

    @Scheduled(fixedDelayString = "${research.recovery-interval}")
    void recoverStuckRuns() {
        Instant deadline = clock.instant().minus(properties.stepTimeout());
        runRepository.findStuckIds(RunStatus.ACTIVE, deadline)
                .forEach(runId -> guarded("recover", runId, () -> orchestrator.recoverIfStuck(runId)));
    }

    /* ***** Private Methods ***** */

    private void guarded(String action, UUID runId, Runnable task) {
        try {
            task.run();
        } catch (OptimisticLockingFailureException e) {
            log.info("Skipped {} of run {}: updated concurrently", action, runId);
        } catch (RuntimeException e) {
            log.error("Failed to {} run {}, will try again on the next tick", action, runId, e);
        }
    }

}
