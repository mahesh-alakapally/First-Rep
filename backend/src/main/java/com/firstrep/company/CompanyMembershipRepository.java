package com.firstrep.company;
import com.firstrep.common.Kinds.MembershipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {
    Optional<CompanyMembership> findByCompanyIdAndUserId(Long companyId, Long userId);
    List<CompanyMembership> findByUserIdAndStatus(Long userId, MembershipStatus status);
    List<CompanyMembership> findByCompanyId(Long companyId);
    @Query("select distinct p.code from CompanyMembership m join m.roles r join r.permissions p where m.user.id = :userId and m.company.id = :companyId and m.status = com.firstrep.common.Kinds.MembershipStatus.ACTIVE")
    List<String> permissionCodes(@Param("userId") Long userId, @Param("companyId") Long companyId);
}
