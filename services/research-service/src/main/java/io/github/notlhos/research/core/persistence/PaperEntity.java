package io.github.notlhos.research.core.persistence;

import io.github.notlhos.research.application.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * arXiv paper shared by every run that found it.
 */
@Getter
@Setter
@Entity
@Table(name = "papers")
@NoArgsConstructor
public class PaperEntity extends AuditedEntity {

    @Id
    private String arxivId;

    private String title;

    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> authors;

    private String published;

    private String url;

    public String embeddingText() {
        return title + "\n" + summary;
    }

}
