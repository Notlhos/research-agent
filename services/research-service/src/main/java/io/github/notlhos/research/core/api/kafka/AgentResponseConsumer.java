package io.github.notlhos.research.core.api.kafka;

import io.github.notlhos.research.application.kafka.KafkaTopics;
import io.github.notlhos.research.application.kafka.model.EmbeddingResponseMessage;
import io.github.notlhos.research.application.kafka.model.PlanResponseMessage;
import io.github.notlhos.research.application.kafka.model.ReportResponseMessage;
import io.github.notlhos.research.core.service.ResearchOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
class AgentResponseConsumer {

    private final ResearchOrchestrator orchestrator;

    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopics.PLAN_RESPONSE)
    void onPlan(String payload) {
        orchestrator.onPlanned(jsonMapper.readValue(payload, PlanResponseMessage.class));
    }

    @KafkaListener(topics = KafkaTopics.EMBEDDING_RESPONSE)
    void onEmbedding(String payload) {
        orchestrator.onEmbedded(jsonMapper.readValue(payload, EmbeddingResponseMessage.class));
    }

    @KafkaListener(topics = KafkaTopics.REPORT_RESPONSE)
    void onReport(String payload) {
        orchestrator.onReportWritten(jsonMapper.readValue(payload, ReportResponseMessage.class));
    }

}
