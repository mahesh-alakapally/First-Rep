package com.firstrep.assessment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CriterionResultRepository extends JpaRepository<CriterionResult, Long> {
    List<CriterionResult> findByAssessmentIdOrderByIdAsc(Long assessmentId);
}
