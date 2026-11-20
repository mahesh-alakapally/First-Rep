package com.firstrep.project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {
    List<RubricCriterion> findByRubricVersionIdOrderBySortOrderAsc(Long rubricVersionId);
    void deleteByRubricVersionId(Long rubricVersionId);
}
