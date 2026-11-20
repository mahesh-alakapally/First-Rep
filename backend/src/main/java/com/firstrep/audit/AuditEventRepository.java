package com.firstrep.audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    @Query("select a from AuditEvent a where (:action is null or a.action = :action) and (:companyId is null or a.companyId = :companyId) order by a.createdAt desc")
    Page<AuditEvent> search(@Param("action") String action, @Param("companyId") Long companyId, Pageable pageable);
}
