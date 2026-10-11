package io.github.notlhos.research.application.kafka;

import io.github.notlhos.research.application.kafka.model.EmbeddingRequestMessage;
import io.github.notlhos.research.application.kafka.model.PlanRequestMessage;
import io.github.notlhos.research.application.kafka.model.ReportRequestMessage;

/**
 * Sends work to the Python agents; answers arrive asynchronously on the matching response topics.
 */
public interface AgentRequestPublisher {

    void requestPlan(PlanRequestMessage request);

    void requestEmbeddings(EmbeddingRequestMessage request);

    void requestReport(ReportRequestMessage request);

}
