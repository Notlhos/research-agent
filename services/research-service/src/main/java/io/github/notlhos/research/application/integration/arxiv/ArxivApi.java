package io.github.notlhos.research.application.integration.arxiv;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = "application/atom+xml")
interface ArxivApi {

    @GetExchange
    String query(
            @RequestParam("search_query") String searchQuery,
            @RequestParam("start") int start,
            @RequestParam("max_results") int maxResults,
            @RequestParam("sortBy") String sortBy);

}
