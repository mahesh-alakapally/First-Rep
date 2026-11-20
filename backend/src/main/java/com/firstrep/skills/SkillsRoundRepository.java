package com.firstrep.skills;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillsRoundRepository extends JpaRepository<SkillsRound, Long> {
    Optional<SkillsRound> findByPublicId(String publicId);
    Optional<SkillsRound> findByProjectId(Long projectId);
}
