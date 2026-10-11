package io.github.notlhos.research.application.kafka;

import io.github.notlhos.research.application.config.ResearchProperties;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
class KafkaConfig {

    private static final long RETRY_INTERVAL_MS = 1_000L;

    private static final long RETRY_ATTEMPTS = 2L;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumed, error) -> new TopicPartition(KafkaTopics.deadLetterOf(consumed.topic()), -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }

    @Bean
    @ConditionalOnBooleanProperty("research.kafka.create-topics")
    KafkaAdmin.NewTopics researchTopics(ResearchProperties properties) {
        ResearchProperties.Kafka kafka = properties.kafka();
        return new KafkaAdmin.NewTopics(KafkaTopics.ALL.stream()
                .flatMap(topic -> Stream.of(topic, KafkaTopics.deadLetterOf(topic)))
                .map(topic -> TopicBuilder.name(topic)
                        .partitions(kafka.partitions())
                        .replicas(kafka.replicationFactor())
                        .build())
                .toArray(NewTopic[]::new));
    }

}
