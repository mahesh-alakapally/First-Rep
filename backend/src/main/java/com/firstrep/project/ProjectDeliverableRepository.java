package com.firstrep.project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectDeliverableRepository extends JpaRepository<ProjectDeliverable, Long> {
    List<ProjectDeliverable> findByProjectIdOrderBySortOrderAsc(Long projectId);
    void deleteByProjectId(Long projectId);
}
