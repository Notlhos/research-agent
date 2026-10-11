package io.github.notlhos.research.application.kafka;

import io.github.notlhos.research.application.kafka.model.EmbeddingRequestMessage;
import io.github.notlhos.research.application.kafka.model.PlanRequestMessage;
import io.github.notlhos.research.application.kafka.model.ReportRequestMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
class AgentRequestPublisherImpl implements AgentRequestPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final JsonMapper jsonMapper;

    @Override
    public void requestPlan(PlanRequestMessage request) {
        send(KafkaTopics.PLAN_REQUEST, request.runId(), request);
    }

    @Override
    public void requestEmbeddings(EmbeddingRequestMessage request) {
        send(KafkaTopics.EMBEDDING_REQUEST, request.runId(), request);
    }

    @Override
    public void requestReport(ReportRequestMessage request) {
        send(KafkaTopics.REPORT_REQUEST, request.runId(), request);
    }

    /* ***** Private Methods ***** */

    private void send(String topic, UUID runId, Object message) {
        kafkaTemplate.send(topic, runId.toString(), jsonMapper.writeValueAsString(message))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to send {} for run {}, recovery will retry the step", topic, runId, error);
                    }
                });
    }

}
