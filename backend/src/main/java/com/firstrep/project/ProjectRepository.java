package com.firstrep.project;
import com.firstrep.common.Kinds.ProjectStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByPublicId(String publicId);
    List<Project> findByCompanyIdOrderByUpdatedAtDesc(Long companyId);
    List<Project> findByStatusNotOrderByUpdatedAtDesc(ProjectStatus status);
    long countByCompanyIdAndStatus(Long companyId, ProjectStatus status);
    @Query("select p from Project p join p.company c where p.status = com.firstrep.common.Kinds.ProjectStatus.PUBLISHED and (:q is null or lower(p.title) like lower(concat('%', :q, '%')) or lower(p.summary) like lower(concat('%', :q, '%')) or lower(p.roleTag) like lower(concat('%', :q, '%'))) and (:role is null or lower(p.roleTag) like lower(concat('%', :role, '%'))) and (:difficulty is null or p.difficulty = :difficulty) and (:companyId is null or c.publicId = :companyId) and (:skill is null or exists (select sk from p.skills sk where lower(sk.name) = lower(:skill))) and (:effortMax is null or p.effortHours <= :effortMax)")
    Page<Project> search(@Param("q") String q, @Param("role") String role,
                         @Param("difficulty") com.firstrep.common.Kinds.Difficulty difficulty,
                         @Param("companyId") String companyId, @Param("skill") String skill,
                         @Param("effortMax") Integer effortMax, Pageable pageable);
}
