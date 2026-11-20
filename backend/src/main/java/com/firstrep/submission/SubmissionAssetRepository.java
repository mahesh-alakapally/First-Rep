package com.firstrep.submission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SubmissionAssetRepository extends JpaRepository<SubmissionAsset, Long> {
    List<SubmissionAsset> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
    Optional<SubmissionAsset> findByPublicId(String publicId);
}
