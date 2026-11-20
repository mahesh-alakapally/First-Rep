package com.firstrep.sharing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ShareRepository extends JpaRepository<CandidateShare, Long> {
    Optional<CandidateShare> findByPublicId(String publicId);
    List<CandidateShare> findByCompanyIdOrderByConsentedAtDesc(Long companyId);
    List<CandidateShare> findByUserIdOrderByConsentedAtDesc(Long userId);
}
