package io.github.notlhos.research.core.mapper;

import io.github.notlhos.research.application.kafka.model.ReportDto;
import io.github.notlhos.research.core.api.web.response.ResearchRunResponse;
import io.github.notlhos.research.core.api.web.response.ResearchRunSummaryResponse;
import io.github.notlhos.research.core.api.web.response.TokenUsageResponse;
import io.github.notlhos.research.core.persistence.PaperEntity;
import io.github.notlhos.research.core.persistence.ResearchRunEntity;
import io.github.notlhos.research.core.persistence.TokenTotals;
import io.github.notlhos.research.core.persistence.TokenUsage;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = PaperMapper.class)
public interface ResearchRunMapper {

    @Mapping(target = "papersFound", expression = "java(run.getFoundPaperIds().size())")
    @Mapping(target = "attempt", source = "run.stepAttempt")
    @Mapping(target = "report", source = "report")
    @Mapping(target = "sources", source = "sources")
    ResearchRunResponse toResponse(ResearchRunEntity run, ReportDto report, List<PaperEntity> sources);

    ResearchRunSummaryResponse toSummary(ResearchRunEntity run);

    TokenUsageResponse toUsage(TokenUsage usage);

    TokenUsageResponse toUsage(TokenTotals totals);

}
