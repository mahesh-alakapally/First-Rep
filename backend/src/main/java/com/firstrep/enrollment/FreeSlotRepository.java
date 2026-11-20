package com.firstrep.enrollment;
import com.firstrep.common.Kinds.SlotStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FreeSlotRepository extends JpaRepository<FreeAllowanceSlot, Long> {
    List<FreeAllowanceSlot> findByStudentIdOrderBySlotIndexAsc(Long studentId);
    Optional<FreeAllowanceSlot> findFirstByStudentIdAndStatusOrderBySlotIndexAsc(Long studentId, SlotStatus status);
    Optional<FreeAllowanceSlot> findByEnrollmentId(Long enrollmentId);
}
