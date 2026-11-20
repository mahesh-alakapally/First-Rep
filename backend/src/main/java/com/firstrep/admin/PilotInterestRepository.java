package com.firstrep.admin;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PilotInterestRepository extends JpaRepository<PilotInterest, Long> {
    List<PilotInterest> findTop100ByOrderByCreatedAtDesc();
}
