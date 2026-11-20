package com.firstrep.assessment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssessmentJobRepository extends JpaRepository<AssessmentJob, Long> {
    Optional<AssessmentJob> findBySubmissionId(Long submissionId);
    Optional<AssessmentJob> findByIdempotencyKey(String idempotencyKey);
}
