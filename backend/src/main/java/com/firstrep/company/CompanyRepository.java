package com.firstrep.company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByPublicId(String publicId);
    Optional<Company> findBySlug(String slug);
    @Query("select distinct p.company from com.firstrep.project.Project p where p.status = com.firstrep.common.Kinds.ProjectStatus.PUBLISHED and p.company.status = com.firstrep.common.Kinds.CompanyStatus.ACTIVE")
    java.util.List<Company> findWithPublishedProjects();
}
