package com.firstrep.assessment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    Optional<Assessment> findBySubmissionId(Long submissionId);
    Optional<Assessment> findByPublicId(String publicId);
}
