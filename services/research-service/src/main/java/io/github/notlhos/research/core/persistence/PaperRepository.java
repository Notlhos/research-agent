package io.github.notlhos.research.core.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperRepository extends JpaRepository<PaperEntity, String> {

    /**
     * Returns the papers with the given ids in the order of the ids; unknown ids are skipped.
     */
    default List<PaperEntity> findAllInOrder(Collection<String> arxivIds) {
        Map<String, PaperEntity> byId = findAllById(arxivIds).stream()
                .collect(Collectors.toMap(PaperEntity::getArxivId, Function.identity()));
        return arxivIds.stream().filter(byId::containsKey).map(byId::get).toList();
    }

}
