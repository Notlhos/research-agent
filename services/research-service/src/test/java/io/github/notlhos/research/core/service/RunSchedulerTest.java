package io.github.notlhos.research.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class RunSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    private static final ResearchProperties PROPERTIES = new ResearchProperties(4, 3, Duration.ofMinutes(5), 8, 12,
            Duration.ofSeconds(2), Duration.ofSeconds(15), null, null, null);

    @Mock
    private ResearchRunRepository runRepository;

    @Mock
    private ResearchOrchestrator orchestrator;

    private RunScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RunScheduler(runRepository, orchestrator, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void admitsOnlyAsManyRunsAsThereAreFreeSlots() {
        UUID first = UUID.randomUUID();
        when(runRepository.countByStatusIn(RunStatus.ACTIVE)).thenReturn(3L);
        when(runRepository.findIdsByStatus(eq(RunStatus.PENDING), argThat(page -> page.getPageSize() == 1)))
                .thenReturn(List.of(first));

        scheduler.admitPendingRuns();

        verify(orchestrator).start(first);
    }

    @Test
    void admitsNothingWhenAllSlotsAreTaken() {
        when(runRepository.countByStatusIn(RunStatus.ACTIVE)).thenReturn(4L);

        scheduler.admitPendingRuns();

        verify(runRepository, never()).findIdsByStatus(any(), any());
        verify(orchestrator, never()).start(any());
    }

    @Test
    void failureOfOneRunDoesNotStopTheOthers() {
        UUID conflicting = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(runRepository.findStuckIds(RunStatus.ACTIVE, NOW.minus(Duration.ofMinutes(5))))
                .thenReturn(List.of(conflicting, broken, healthy));
        doThrow(new OptimisticLockingFailureException("concurrent update"))
                .when(orchestrator).recoverIfStuck(conflicting);
        doThrow(new IllegalStateException("unexpected")).when(orchestrator).recoverIfStuck(broken);

        scheduler.recoverStuckRuns();

        verify(orchestrator).recoverIfStuck(healthy);
    }

}
