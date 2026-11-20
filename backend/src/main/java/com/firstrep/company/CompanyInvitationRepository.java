package com.firstrep.company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {
    Optional<CompanyInvitation> findByTokenHash(String tokenHash);
}
