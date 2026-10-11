package io.github.notlhos.research.application.integration.milvus;

import io.github.notlhos.research.application.integration.milvus.model.ScoredPaper;
import io.github.notlhos.research.application.kafka.model.EmbeddingVectorDto;
import java.util.Collection;
import java.util.List;

public interface PaperVectorStore {

    void upsert(List<EmbeddingVectorDto> vectors);

    /**
     * Returns the candidates closest to the query vector, most similar first. The index is shared by all runs,
     * so callers restrict the search to the papers their own run found.
     */
    List<ScoredPaper> search(float[] query, Collection<String> candidateIds, int limit);

}
