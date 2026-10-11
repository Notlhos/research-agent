package io.github.notlhos.research.application.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("research")
public record ResearchProperties(
        @Positive int maxActiveRuns,
        @Positive int maxStepAttempts,
        @NotNull Duration stepTimeout,
        @Positive int papersPerQuery,
        @Positive int retrievalTopK,
        @NotNull Duration admissionInterval,
        @NotNull Duration recoveryInterval,
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Arxiv arxiv,
        @Valid @NotNull Milvus milvus) {

    public record Kafka(boolean createTopics, @Positive int partitions, @Positive int replicationFactor) {
    }

    public record Arxiv(@NotBlank String baseUrl, @NotNull Duration minInterval, @NotNull Duration timeout) {
    }

    public record Milvus(@NotBlank String uri, @NotBlank String collection, @Positive int dimension) {
    }

}
