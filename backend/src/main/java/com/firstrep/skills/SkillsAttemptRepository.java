package com.firstrep.skills;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillsAttemptRepository extends JpaRepository<SkillsAttempt, Long> {
    Optional<SkillsAttempt> findByPublicId(String publicId);
    Optional<SkillsAttempt> findFirstByRoundIdAndStudentIdAndStatusOrderByStartedAtDesc(Long roundId, Long studentId, com.firstrep.common.Kinds.AttemptStatus status);
    Optional<SkillsAttempt> findFirstByRoundIdAndStudentIdOrderByStartedAtDesc(Long roundId, Long studentId);
}
