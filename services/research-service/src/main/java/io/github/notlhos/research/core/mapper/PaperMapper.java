package io.github.notlhos.research.core.mapper;

import io.github.notlhos.research.application.integration.arxiv.model.ArxivPaper;
import io.github.notlhos.research.application.kafka.model.SourceDocumentDto;
import io.github.notlhos.research.core.api.web.response.SourceResponse;
import io.github.notlhos.research.core.persistence.PaperEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaperMapper {

    PaperEntity toEntity(ArxivPaper paper);

    SourceDocumentDto toSourceDocument(PaperEntity paper);

    SourceResponse toSource(PaperEntity paper);

}
