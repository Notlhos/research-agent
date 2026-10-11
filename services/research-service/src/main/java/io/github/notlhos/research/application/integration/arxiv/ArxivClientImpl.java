package io.github.notlhos.research.application.integration.arxiv;

import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * arXiv search client that follows the arXiv API terms: one request at a time and at least
 * {@code research.arxiv.min-interval} between requests. The lock is therefore held for the whole HTTP call on
 * purpose; the request timeout bounds how long other runs can wait behind it.
 */
@Component
class ArxivClientImpl implements ArxivClient {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "by", "for", "from", "how", "in", "into", "is", "of",
            "on", "or", "the", "to", "using", "via", "what", "with");

    private static final String SORT_BY_RELEVANCE = "relevance";

    private final ArxivApi arxivApi;

    private final ArxivFeedParser feedParser;

    private final Duration minInterval;

    private final ReentrantLock rateLimit = new ReentrantLock();

    private long lastCallNanos;

    ArxivClientImpl(ArxivApi arxivApi, ArxivFeedParser feedParser, ResearchProperties properties) {
        this.arxivApi = arxivApi;
        this.feedParser = feedParser;
        this.minInterval = properties.arxiv().minInterval();
    }

    @Override
    public List<ArxivPaper> search(String query, int limit) {
        rateLimit.lock();
        try {
            waitForRateLimit();
            String feed = arxivApi.query(buildQuery(query), 0, limit, SORT_BY_RELEVANCE);
            return feed == null ? List.of() : feedParser.parse(feed);
        } finally {
            rateLimit.unlock();
        }
    }

    static String buildQuery(String query) {
        return Arrays.stream(query.strip().split("\\s+"))
                .filter(term -> !STOP_WORDS.contains(term.toLowerCase(Locale.ROOT)))
                .map(term -> "all:" + term)
                .collect(Collectors.joining(" AND "));
    }

    /* ***** Private Methods ***** */

    private void waitForRateLimit() {
        long waitNanos = lastCallNanos + minInterval.toNanos() - System.nanoTime();
        if (waitNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the arXiv rate limit", e);
            }
        }
        lastCallNanos = System.nanoTime();
    }

}
