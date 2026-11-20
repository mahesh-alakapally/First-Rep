package com.firstrep.identity;
import com.firstrep.common.Kinds.TokenPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {
    Optional<EmailToken> findFirstByTokenHashAndPurposeAndUsedAtIsNull(String tokenHash, TokenPurpose purpose);
}
