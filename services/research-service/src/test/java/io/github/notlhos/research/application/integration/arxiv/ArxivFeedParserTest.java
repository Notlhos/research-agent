package io.github.notlhos.research.application.integration.arxiv;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArxivFeedParserTest {

    private static final String FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>http://arxiv.org/abs/2401.01234v2</id>
                <published>2024-01-03T18:00:00Z</published>
                <title>Self-Correcting
                  Agents for Tool Use</title>
                <summary>  We study agents that
                  repair their own outputs. </summary>
                <author><name>Ada Lovelace</name></author>
                <author><name>Alan Turing</name></author>
              </entry>
              <entry>
                <id>http://arxiv.org/abs/hep-th/9901001v1</id>
                <published>1999-01-01T00:00:00Z</published>
                <title>Old Style Identifier</title>
                <summary>Legacy id format.</summary>
                <author><name>Grace Hopper</name></author>
              </entry>
            </feed>
            """;

    private final ArxivFeedParser parser = new ArxivFeedParser();

    @Test
    void normalizesIdsAndWhitespace() {
        List<ArxivPaper> papers = parser.parse(FEED);

        assertThat(papers).extracting(ArxivPaper::arxivId).containsExactly("2401.01234", "hep-th/9901001");
        ArxivPaper first = papers.getFirst();
        assertThat(first.title()).isEqualTo("Self-Correcting Agents for Tool Use");
        assertThat(first.summary()).isEqualTo("We study agents that repair their own outputs.");
        assertThat(first.authors()).containsExactly("Ada Lovelace", "Alan Turing");
        assertThat(first.published()).isEqualTo("2024-01-03");
        assertThat(first.url()).isEqualTo("https://arxiv.org/abs/2401.01234");
    }

    @Test
    void buildsConjunctiveQueryWithoutStopWords() {
        assertThat(ArxivClientImpl.buildQuery(" llm agent  evaluation "))
                .isEqualTo("all:llm AND all:agent AND all:evaluation");
        assertThat(ArxivClientImpl.buildQuery("Evaluation of agents with judges"))
                .isEqualTo("all:Evaluation AND all:agents AND all:judges");
    }

}
