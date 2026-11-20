package com.firstrep.admin;
import com.firstrep.common.Kinds.DataRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DataRequestRepository extends JpaRepository<DataRequest, Long> {
    Optional<DataRequest> findByPublicId(String publicId);
    List<DataRequest> findByStatusOrderByCreatedAtAsc(DataRequestStatus status);
    List<DataRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
