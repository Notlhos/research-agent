package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.kafka.model.LlmUsageDto;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ResearchMetrics {

    private final MeterRegistry registry;

    ResearchMetrics(MeterRegistry registry, ResearchRunRepository runRepository) {
        this.registry = registry;
        Gauge.builder("research.runs.active", runRepository,
                        repository -> repository.countByStatusIn(RunStatus.ACTIVE))
                .description("Runs currently in an active step")
                .register(registry);
        Gauge.builder("research.runs.pending", runRepository,
                        repository -> repository.countByStatusIn(Set.of(RunStatus.PENDING)))
                .description("Runs waiting for admission")
                .register(registry);
    }

    void stepCompleted(RunStatus step, Duration duration) {
        registry.timer("research.step.duration", "step", step.name()).record(duration);
    }

    void stepRetried(RunStatus step) {
        registry.counter("research.step.retries", "step", step.name()).increment();
    }

    void runFinished(RunStatus status) {
        registry.counter("research.runs.finished", "status", status.name()).increment();
    }

    void tokensUsed(LlmUsageDto usage) {
        if (usage == null) {
            return;
        }
        registry.counter("research.llm.tokens", "kind", "input").increment(usage.inputTokens());
        registry.counter("research.llm.tokens", "kind", "output").increment(usage.outputTokens());
        registry.counter("research.llm.tokens", "kind", "cache_read").increment(usage.cacheReadInputTokens());
    }

}
