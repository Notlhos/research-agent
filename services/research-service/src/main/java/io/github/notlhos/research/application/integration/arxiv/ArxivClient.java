package io.github.notlhos.research.application.integration.arxiv;

import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import java.util.List;

public interface ArxivClient {

    /**
     * Returns the most relevant papers matching all terms of the query.
     *
     * <p>Calls are serialized and spaced by {@code research.arxiv.min-interval}, so concurrent runs share one
     * request budget instead of being throttled by arXiv.
     */
    List<ArxivPaper> search(String query, int limit);

}
