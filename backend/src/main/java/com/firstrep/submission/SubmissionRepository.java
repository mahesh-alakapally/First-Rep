package com.firstrep.submission;
import com.firstrep.common.Kinds.SubmissionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByPublicId(String publicId);
    List<Submission> findByEnrollmentIdOrderByRevisionNumberDesc(Long enrollmentId);
    Optional<Submission> findFirstByEnrollmentIdAndStatus(Long enrollmentId, SubmissionStatus status);
    @Query("select coalesce(max(s.revisionNumber), 0) from Submission s where s.enrollment.id = :enrollmentId")
    int maxRevision(Long enrollmentId);
    @Query("select count(s) from Submission s where s.enrollment.id = :enrollmentId and s.status = com.firstrep.common.Kinds.SubmissionStatus.SUBMITTED")
    long countSubmitted(Long enrollmentId);
    @Query("select s from Submission s join s.enrollment e join e.project p where p.company.id = :companyId and s.status = com.firstrep.common.Kinds.SubmissionStatus.SUBMITTED order by s.submittedAt desc")
    List<Submission> findSubmittedForCompany(Long companyId);
}
