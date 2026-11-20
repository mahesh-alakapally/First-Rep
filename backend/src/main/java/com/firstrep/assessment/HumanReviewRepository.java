package com.firstrep.assessment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HumanReviewRepository extends JpaRepository<HumanReview, Long> {
    List<HumanReview> findByAssessmentIdOrderByCreatedAtAsc(Long assessmentId);
}
