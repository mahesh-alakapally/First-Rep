package com.firstrep.certificate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByPublicId(String publicId);
    Optional<Certificate> findByVerificationCode(String verificationCode);
    Optional<Certificate> findByUserIdAndProjectId(Long userId, Long projectId);
    List<Certificate> findByUserIdOrderByIssuedAtDesc(Long userId);
}
