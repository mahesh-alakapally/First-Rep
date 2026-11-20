package com.firstrep.project;
import com.firstrep.common.Kinds.RubricStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RubricVersionRepository extends JpaRepository<RubricVersion, Long> {
    List<RubricVersion> findByProjectIdOrderByVersionNumberDesc(Long projectId);
    Optional<RubricVersion> findFirstByProjectIdAndStatusOrderByVersionNumberDesc(Long projectId, RubricStatus status);
}
