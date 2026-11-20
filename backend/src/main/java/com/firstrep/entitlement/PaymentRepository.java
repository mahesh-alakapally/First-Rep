package com.firstrep.entitlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
