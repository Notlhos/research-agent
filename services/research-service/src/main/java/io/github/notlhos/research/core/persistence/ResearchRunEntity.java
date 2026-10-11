package io.github.notlhos.research.core.persistence;

import io.github.notlhos.research.application.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Research run and the results of its steps.
 *
 * <p>State changes only through the transition methods, so the orchestrator is the single place that decides
 * which step a run is in and which attempt of it is current.
 *
 * <p>{@code attempt} numbers every dispatch of the run and never decreases, even when a failed run is requeued.
 * Agents echo it back, so a late response can never be mistaken for the answer to a later dispatch.
 * {@code stepAttempt} counts tries of the current step and drives the retry budget.
 */
@Getter
@Entity
@Table(name = "research_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchRunEntity extends AuditedEntity {

    @Id
    private UUID id;

    private String topic;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    private int attempt;

    private int stepAttempt;

    private Instant stepStartedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> queries = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> foundPaperIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> sourcePaperIds = new ArrayList<>();

    private String reportJson;

    @Embedded
    private TokenUsage usage = new TokenUsage();

    private String error;

    @Version
    private Long version;

    public static ResearchRunEntity create(String topic) {
        ResearchRunEntity run = new ResearchRunEntity();
        run.id = UUID.randomUUID();
        run.topic = topic;
        run.status = RunStatus.PENDING;
        return run;
    }

    public boolean awaits(RunStatus step, int expectedAttempt) {
        return status == step && attempt == expectedAttempt;
    }

    public void enter(RunStatus step, Instant now) {
        status = step;
        attempt++;
        stepAttempt = 1;
        stepStartedAt = now;
    }

    public void retry(String reason, Instant now) {
        attempt++;
        stepAttempt++;
        error = reason;
        stepStartedAt = now;
    }

    public void finish(RunStatus terminal, String reason) {
        status = terminal;
        error = reason;
        stepStartedAt = null;
    }

    /**
     * Puts a finished run back in the admission queue; results of the previous execution are discarded,
     * accumulated token usage is kept.
     */
    public void requeue() {
        status = RunStatus.PENDING;
        stepAttempt = 0;
        stepStartedAt = null;
        error = null;
        reportJson = null;
        queries = new ArrayList<>();
        foundPaperIds = new ArrayList<>();
        sourcePaperIds = new ArrayList<>();
    }

    public void recordQueries(List<String> planned) {
        queries = new ArrayList<>(planned);
    }

    public void recordFoundPapers(List<String> arxivIds) {
        foundPaperIds = new ArrayList<>(arxivIds);
    }

    /**
     * Stores the ids of the papers retrieved for the writer, most relevant first.
     */
    public void recordSources(List<String> arxivIds) {
        sourcePaperIds = new ArrayList<>(arxivIds);
    }

    public void recordReport(String json) {
        reportJson = json;
    }

}
