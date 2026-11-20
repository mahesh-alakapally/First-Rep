package com.firstrep.entitlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {
    Optional<CheckoutSession> findByPublicId(String publicId);
}
