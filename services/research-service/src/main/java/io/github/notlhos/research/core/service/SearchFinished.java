package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import java.util.List;
import java.util.UUID;

record SearchFinished(UUID runId, int attempt, List<ArxivPaper> papers, String error) {

    static SearchFinished success(UUID runId, int attempt, List<ArxivPaper> papers) {
        return new SearchFinished(runId, attempt, papers, null);
    }

    static SearchFinished failure(UUID runId, int attempt, String error) {
        return new SearchFinished(runId, attempt, List.of(), error);
    }

}
