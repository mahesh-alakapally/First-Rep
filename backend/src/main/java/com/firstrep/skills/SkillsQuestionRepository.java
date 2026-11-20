package com.firstrep.skills;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillsQuestionRepository extends JpaRepository<SkillsQuestion, Long> {
    List<SkillsQuestion> findByRoundIdOrderBySortOrderAsc(Long roundId);
    Optional<SkillsQuestion> findByPublicId(String publicId);
    void deleteByRoundId(Long roundId);
}
