package io.github.notlhos.research.core.service;

import io.github.notlhos.research.core.persistence.ResearchRunEntity;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.util.List;
import java.util.UUID;

record RunSnapshot(
        UUID runId,
        RunStatus status,
        int attempt,
        String topic,
        List<String> queries,
        List<String> foundPaperIds,
        List<String> sourcePaperIds) {

    static RunSnapshot of(ResearchRunEntity run) {
        return new RunSnapshot(
                run.getId(),
                run.getStatus(),
                run.getAttempt(),
                run.getTopic(),
                List.copyOf(run.getQueries()),
                List.copyOf(run.getFoundPaperIds()),
                List.copyOf(run.getSourcePaperIds()));
    }

}
