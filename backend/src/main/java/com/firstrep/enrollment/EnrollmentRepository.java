package com.firstrep.enrollment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EnrollmentRepository extends JpaRepository<ProjectEnrollment, Long> {
    Optional<ProjectEnrollment> findByPublicId(String publicId);
    Optional<ProjectEnrollment> findByProjectIdAndStudentId(Long projectId, Long studentId);
    List<ProjectEnrollment> findByStudentIdOrderByEnrolledAtDesc(Long studentId);
    List<ProjectEnrollment> findByProjectId(Long projectId);
}
