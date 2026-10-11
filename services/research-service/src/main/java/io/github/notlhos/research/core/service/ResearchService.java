package io.github.notlhos.research.core.service;

import io.github.notlhos.research.application.exception.ErrorCode;
import io.github.notlhos.research.application.exception.NotFoundException;
import io.github.notlhos.research.application.kafka.model.ReportDto;
import io.github.notlhos.research.core.api.web.request.CreateResearchRequest;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.ResearchRunSummaryResponse;
import io.github.notlhos.research.core.api.web.response.RunStatisticsResponse;
import io.github.notlhos.research.core.mapper.ResearchRunMapper;
import io.github.notlhos.research.core.persistence.PaperEntity;
import io.github.notlhos.research.core.persistence.PaperRepository;
import io.github.notlhos.research.core.persistence.ResearchRunEntity;
import io.github.notlhos.research.core.persistence.ResearchRunRepository;
import io.github.notlhos.research.core.persistence.RunStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ResearchService {

    private final ResearchRunRepository runRepository;

    private final PaperRepository paperRepository;

    private final ResearchOrchestrator orchestrator;

    private final ResearchRunMapper runMapper;

    private final ReportRenderer reportRenderer;

    private final JsonMapper jsonMapper;

    /**
     * Queues a run for the topic; it starts once admission control grants it a slot.
     */
    @Transactional
    public ResearchRunResponse create(CreateResearchRequest request) {
        return toResponse(orchestrator.create(request.topic()));
    }

    @Transactional(readOnly = true)
    public ResearchRunResponse getById(UUID runId) {
        return toResponse(findRun(runId));
    }

    @Transactional(readOnly = true)
    public Page<ResearchRunSummaryResponse> findAll(Pageable pageable) {
        return runRepository.findAll(pageable).map(runMapper::toSummary);
    }

    /**
     * Renders the report of a run as Markdown with a reference list.
     */
    @Transactional(readOnly = true)
    public String renderReport(UUID runId) {
        return reportRenderer.markdown(toResponse(findRun(runId)));
    }

    /**
     * Cancels a run that has not finished; responses still in flight for it are ignored.
     */
    @Transactional
    public ResearchRunResponse cancel(UUID runId) {
        orchestrator.cancel(runId);
        return flushedResponse(runId);
    }

    /**
     * Puts a failed run back into the admission queue to be executed again from planning.
     */
    @Transactional
    public ResearchRunResponse retry(UUID runId) {
        orchestrator.retry(runId);
        return flushedResponse(runId);
    }

    @Transactional(readOnly = true)
    public RunStatisticsResponse statistics() {
        Map<RunStatus, Long> runsByStatus = new EnumMap<>(RunStatus.class);
        runRepository.countPerStatus().forEach(count -> runsByStatus.put(count.status(), count.runs()));
        return new RunStatisticsResponse(runsByStatus, runMapper.toUsage(runRepository.sumTokenUsage()));
    }

    /* ***** Private Methods ***** */

    private ResearchRunEntity findRun(UUID runId) {
        return runRepository.findById(runId).orElseThrow(() -> new NotFoundException(ErrorCode.RUN_NOT_FOUND,
                "Research run " + runId + " not found"));
    }

    private ResearchRunResponse flushedResponse(UUID runId) {
        runRepository.flush();
        return toResponse(findRun(runId));
    }

    private ResearchRunResponse toResponse(ResearchRunEntity run) {
        ReportDto report = run.getReportJson() == null
                ? null
                : jsonMapper.readValue(run.getReportJson(), ReportDto.class);
        Set<String> cited = report == null ? Set.of() : Set.copyOf(report.citedIds());
        List<PaperEntity> sources = paperRepository.findAllInOrder(run.getSourcePaperIds()).stream()
                .filter(paper -> cited.contains(paper.getArxivId()))
                .toList();
        return runMapper.toResponse(run, report, sources);
    }

}
