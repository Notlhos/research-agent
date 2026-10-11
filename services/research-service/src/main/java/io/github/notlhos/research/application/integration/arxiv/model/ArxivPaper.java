package io.github.notlhos.research.application.integration.arxiv.model;

import java.util.List;

/**
 * Paper returned by an arXiv search, with the version suffix stripped from its id.
 */
public record ArxivPaper(
        String arxivId, String title, String summary, List<String> authors, String published, String url) {
}
