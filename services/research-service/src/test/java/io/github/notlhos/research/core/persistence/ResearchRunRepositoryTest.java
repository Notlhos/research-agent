package io.github.notlhos.research.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.notlhos.research.TestcontainersConfiguration;
import io.github.notlhos.research.application.config.JpaAuditingConfig;
import io.github.notlhos.research.application.kafka.model.LlmUsageDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ResearchRunRepositoryTest {

    @Autowired
    private ResearchRunRepository runRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Test
    void liquibaseSchemaMatchesEntitiesAndAdmissionQueriesWork() {
        final ResearchRunEntity pending = runRepository.saveAndFlush(ResearchRunEntity.create("pending topic"));
        ResearchRunEntity active = ResearchRunEntity.create("active topic");
        active.enter(RunStatus.WRITING, Instant.now().minusSeconds(600));
        active.recordQueries(List.of("agents", "tools"));
        runRepository.saveAndFlush(active);

        assertThat(runRepository.countByStatusIn(RunStatus.ACTIVE)).isEqualTo(1);
        assertThat(runRepository.findIdsByStatus(RunStatus.PENDING, PageRequest.of(0, 5)))
                .containsExactly(pending.getId());
        assertThat(runRepository.findStuckIds(RunStatus.ACTIVE, Instant.now())).containsExactly(active.getId());
        assertThat(runRepository.findById(active.getId()).orElseThrow().getQueries())
                .containsExactly("agents", "tools");
    }

    @Test
    void changesOutsideRequestsAreAuditedAsSystem() {
        ResearchRunEntity run = runRepository.saveAndFlush(ResearchRunEntity.create("audited topic"));

        assertThat(run.getCreatedAt()).isNotNull();
        assertThat(run.getCreatedBy()).isEqualTo(JpaAuditingConfig.SYSTEM_AUDITOR);
        assertThat(run.getModifiedBy()).isEqualTo(JpaAuditingConfig.SYSTEM_AUDITOR);
    }

    @Test
    void statisticsAggregateStatusesAndTokens() {
        ResearchRunEntity done = ResearchRunEntity.create("done topic");
        done.getUsage().add(new LlmUsageDto(2, 1_000, 300, 200, 50));
        done.finish(RunStatus.DONE, null);
        runRepository.saveAndFlush(done);
        runRepository.saveAndFlush(ResearchRunEntity.create("pending topic"));

        assertThat(runRepository.countPerStatus())
                .containsExactlyInAnyOrder(new StatusCount(RunStatus.DONE, 1), new StatusCount(RunStatus.PENDING, 1));
        assertThat(runRepository.sumTokenUsage()).isEqualTo(new TokenTotals(2, 1_000, 300, 200, 50));
    }

    @Test
    void papersKeepRequestedOrder() {
        paperRepository.saveAll(List.of(paper("a"), paper("b")));

        assertThat(paperRepository.findAllInOrder(List.of("b", "missing", "a")))
                .extracting(PaperEntity::getArxivId)
                .containsExactly("b", "a");
    }

    private static PaperEntity paper(String arxivId) {
        PaperEntity paper = new PaperEntity();
        paper.setArxivId(arxivId);
        paper.setTitle("Title " + arxivId);
        paper.setSummary("Summary");
        paper.setAuthors(List.of("Author"));
        paper.setPublished("2024-01-01");
        paper.setUrl("https://arxiv.org/abs/" + arxivId);
        return paper;
    }

}
