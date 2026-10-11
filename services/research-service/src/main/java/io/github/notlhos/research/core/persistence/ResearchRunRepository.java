package io.github.notlhos.research.core.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResearchRunRepository extends JpaRepository<ResearchRunEntity, UUID> {

    long countByStatusIn(Collection<RunStatus> statuses);

    @Query("select r.id from ResearchRunEntity r where r.status = :status order by r.createdAt")
    List<UUID> findIdsByStatus(RunStatus status, Pageable page);

    @Query("select r.id from ResearchRunEntity r where r.status in :statuses and r.stepStartedAt < :before")
    List<UUID> findStuckIds(Collection<RunStatus> statuses, Instant before);

    @Query("select new io.github.notlhos.research.core.persistence.StatusCount(r.status, count(r))"
            + " from ResearchRunEntity r group by r.status")
    List<StatusCount> countPerStatus();

    @Query("select new io.github.notlhos.research.core.persistence.TokenTotals("
            + "coalesce(sum(r.usage.llmCalls), 0L), coalesce(sum(r.usage.inputTokens), 0L),"
            + " coalesce(sum(r.usage.outputTokens), 0L), coalesce(sum(r.usage.cacheReadInputTokens), 0L),"
            + " coalesce(sum(r.usage.cacheCreationInputTokens), 0L)) from ResearchRunEntity r")
    TokenTotals sumTokenUsage();

}
