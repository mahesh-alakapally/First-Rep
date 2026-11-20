package com.firstrep.submission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SubmissionLinkRepository extends JpaRepository<SubmissionLink, Long> {
    List<SubmissionLink> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
}
