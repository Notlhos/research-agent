package io.github.notlhos.research;

import io.github.notlhos.research.application.kafka.KafkaTopics;
import io.github.notlhos.research.application.kafka.model.EmbeddingRequestMessage;
import io.github.notlhos.research.application.kafka.model.EmbeddingResponseMessage;
import io.github.notlhos.research.application.kafka.model.EmbeddingVectorDto;
import io.github.notlhos.research.application.kafka.model.LlmUsageDto;
import io.github.notlhos.research.application.kafka.model.PlanRequestMessage;
import io.github.notlhos.research.application.kafka.model.PlanResponseMessage;
import io.github.notlhos.research.application.kafka.model.ReportDto;
import io.github.notlhos.research.application.kafka.model.ReportRequestMessage;
import io.github.notlhos.research.application.kafka.model.ReportResponseMessage;
import io.github.notlhos.research.application.kafka.model.ReportSectionDto;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class FakeAgents {

    static final LlmUsageDto USAGE = new LlmUsageDto(1, 100, 40, 0, 0);

    private static final String GROUP = "fake-agents";

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final JsonMapper jsonMapper;

    private final List<String> deadLetters = new CopyOnWriteArrayList<>();

    FakeAgents(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    List<String> deadLetters() {
        return deadLetters;
    }

    @KafkaListener(topics = KafkaTopics.PLAN_REQUEST, groupId = GROUP)
    void plan(String payload) {
        PlanRequestMessage request = jsonMapper.readValue(payload, PlanRequestMessage.class);
        reply(KafkaTopics.PLAN_RESPONSE, request.runId(),
                new PlanResponseMessage(request.runId(), request.attempt(), List.of(request.topic()), USAGE, null));
    }

    @KafkaListener(topics = KafkaTopics.EMBEDDING_REQUEST, groupId = GROUP)
    void embed(String payload) {
        EmbeddingRequestMessage request = jsonMapper.readValue(payload, EmbeddingRequestMessage.class);
        List<EmbeddingVectorDto> vectors = request.items().stream()
                .map(item -> new EmbeddingVectorDto(item.id(), new float[] {1f, 0f}))
                .toList();
        reply(KafkaTopics.EMBEDDING_RESPONSE, request.runId(), new EmbeddingResponseMessage(
                request.runId(), request.attempt(), request.purpose(), vectors, null));
    }

    @KafkaListener(topics = KafkaTopics.REPORT_REQUEST, groupId = GROUP)
    void write(String payload) {
        ReportRequestMessage request = jsonMapper.readValue(payload, ReportRequestMessage.class);
        String cited = request.sources().getFirst().arxivId();
        ReportDto report = new ReportDto("Report on " + request.topic(), "Summary [" + cited + "].",
                List.of(new ReportSectionDto("Findings", "Evidence [" + cited + "].")), List.of(cited));
        reply(KafkaTopics.REPORT_RESPONSE, request.runId(),
                new ReportResponseMessage(request.runId(), request.attempt(), report, USAGE, null));
    }

    @KafkaListener(topics = KafkaTopics.PLAN_RESPONSE + KafkaTopics.DEAD_LETTER_SUFFIX, groupId = GROUP)
    void collectDeadLetter(String payload) {
        deadLetters.add(payload);
    }

    /* ***** Private Methods ***** */

    private void reply(String topic, UUID runId, Object message) {
        kafkaTemplate.send(topic, runId.toString(), jsonMapper.writeValueAsString(message));
    }

}
