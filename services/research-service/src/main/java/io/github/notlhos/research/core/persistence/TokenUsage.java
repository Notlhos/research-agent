package io.github.notlhos.research.core.persistence;

import io.github.notlhos.research.application.kafka.model.LlmUsageDto;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
public class TokenUsage {

    private int llmCalls;

    private long inputTokens;

    private long outputTokens;

    private long cacheReadInputTokens;

    private long cacheCreationInputTokens;

    /**
     * Adds the usage reported by an agent; {@code null} means the agent reported none.
     */
    public void add(LlmUsageDto usage) {
        if (usage == null) {
            return;
        }
        llmCalls += usage.llmCalls();
        inputTokens += usage.inputTokens();
        outputTokens += usage.outputTokens();
        cacheReadInputTokens += usage.cacheReadInputTokens();
        cacheCreationInputTokens += usage.cacheCreationInputTokens();
    }

}
