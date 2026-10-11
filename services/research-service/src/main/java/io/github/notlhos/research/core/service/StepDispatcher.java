package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.kafka.AgentRequestPublisher;
import io.github.notlhos.research.application.kafka.model.EmbeddingItemDto;
import io.github.notlhos.research.application.kafka.model.EmbeddingPurpose;
import io.github.notlhos.research.application.kafka.model.EmbeddingRequestMessage;
import io.github.notlhos.research.application.kafka.model.PlanRequestMessage;
import io.github.notlhos.research.application.kafka.model.ReportRequestMessage;
import io.github.notlhos.research.application.kafka.model.SourceDocumentDto;
import io.github.notlhos.research.core.mapper.PaperMapper;
import io.github.notlhos.research.core.persistence.PaperRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class StepDispatcher {

    static final String TOPIC_VECTOR_ID = "topic";

    private final Map<RunStatus, Consumer<RunSnapshot>> actions;

    private final PaperRepository paperRepository;

    private final PaperMapper paperMapper;

    StepDispatcher(
            AgentRequestPublisher agents, SearchStep search, PaperRepository paperRepository, PaperMapper paperMapper) {
        this.paperRepository = paperRepository;
        this.paperMapper = paperMapper;
        this.actions = Map.of(
                RunStatus.PLANNING, run -> agents.requestPlan(planRequest(run)),
                RunStatus.SEARCHING, run -> search.submit(run.runId(), run.attempt(), run.queries()),
                RunStatus.INDEXING, run -> agents.requestEmbeddings(documentsRequest(run)),
                RunStatus.RETRIEVING, run -> agents.requestEmbeddings(topicRequest(run)),
                RunStatus.WRITING, run -> agents.requestReport(reportRequest(run)));
    }

    void dispatchAfterCommit(RunSnapshot run) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(run);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(run);
            }
        });
    }

    /* ***** Private Methods ***** */

    private void dispatch(RunSnapshot run) {
        actions.get(run.status()).accept(run);
    }

    private static PlanRequestMessage planRequest(RunSnapshot run) {
        return new PlanRequestMessage(run.runId(), run.attempt(), run.topic());
    }

    private EmbeddingRequestMessage documentsRequest(RunSnapshot run) {
        List<EmbeddingItemDto> items = paperRepository.findAllInOrder(run.foundPaperIds()).stream()
                .map(paper -> new EmbeddingItemDto(paper.getArxivId(), paper.embeddingText()))
                .toList();
        return new EmbeddingRequestMessage(run.runId(), run.attempt(), EmbeddingPurpose.DOCUMENTS, items);
    }

    private static EmbeddingRequestMessage topicRequest(RunSnapshot run) {
        List<EmbeddingItemDto> items = List.of(new EmbeddingItemDto(TOPIC_VECTOR_ID, run.topic()));
        return new EmbeddingRequestMessage(run.runId(), run.attempt(), EmbeddingPurpose.QUERY, items);
    }

    private ReportRequestMessage reportRequest(RunSnapshot run) {
        List<SourceDocumentDto> sources = paperRepository.findAllInOrder(run.sourcePaperIds()).stream()
                .map(paperMapper::toSourceDocument)
                .toList();
        return new ReportRequestMessage(run.runId(), run.attempt(), run.topic(), sources);
    }

}
