package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.application.integration.arxiv.ArxivClient;
import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SearchStep {

    private final ArxivClient arxivClient;

    private final ResearchProperties properties;

    private final ApplicationEventPublisher events;

    private final TaskExecutor executor;

    SearchStep(
            ArxivClient arxivClient,
            ResearchProperties properties,
            ApplicationEventPublisher events,
            @Qualifier("searchExecutor") TaskExecutor executor) {
        this.arxivClient = arxivClient;
        this.properties = properties;
        this.events = events;
        this.executor = executor;
    }

    void submit(UUID runId, int attempt, List<String> queries) {
        executor.execute(() -> publish(search(runId, attempt, queries)));
    }

    /* ***** Private Methods ***** */

    private void publish(SearchFinished result) {
        try {
            events.publishEvent(result);
        } catch (RuntimeException e) {
            log.error("Could not record search results of run {}, recovery will retry the step", result.runId(), e);
        }
    }

    private SearchFinished search(UUID runId, int attempt, List<String> queries) {
        try {
            Map<String, ArxivPaper> found = new LinkedHashMap<>();
            for (String query : queries) {
                arxivClient.search(query, properties.papersPerQuery())
                        .forEach(paper -> found.putIfAbsent(paper.arxivId(), paper));
            }
            return SearchFinished.success(runId, attempt, List.copyOf(found.values()));
        } catch (RuntimeException e) {
            log.warn("arXiv search failed for run {}", runId, e);
            return SearchFinished.failure(runId, attempt, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

}
