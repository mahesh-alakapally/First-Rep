package com.firstrep.skills;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillsAnswerRepository extends JpaRepository<SkillsAnswer, Long> {
    List<SkillsAnswer> findByAttemptId(Long attemptId);
}
