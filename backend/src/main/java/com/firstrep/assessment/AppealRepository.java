package com.firstrep.assessment;
import com.firstrep.common.Kinds.AppealStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AppealRepository extends JpaRepository<Appeal, Long> {
    Optional<Appeal> findByPublicId(String publicId);
    List<Appeal> findByAssessmentIdOrderByCreatedAtDesc(Long assessmentId);
    List<Appeal> findByStatusOrderByCreatedAtAsc(AppealStatus status);
    Optional<Appeal> findFirstByAssessmentIdAndStatus(Long assessmentId, AppealStatus status);
}
