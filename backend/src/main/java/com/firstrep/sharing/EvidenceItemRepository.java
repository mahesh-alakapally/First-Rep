package com.firstrep.sharing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, Long> {
    List<EvidenceItem> findByShareId(Long shareId);
}
