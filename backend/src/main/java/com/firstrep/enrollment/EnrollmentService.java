package com.firstrep.enrollment;

import com.firstrep.common.AppException;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.AccessGrantType;
import com.firstrep.common.Kinds.EnrollmentStatus;
import com.firstrep.common.Kinds.ProjectStatus;
import com.firstrep.common.Kinds.SlotStatus;
import com.firstrep.common.Kinds.SubmissionStatus;
import com.firstrep.entitlement.BillingService;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import com.firstrep.project.Project;
import com.firstrep.project.ProjectRepository;
import com.firstrep.submission.SubmissionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {
    private static final EnumSet<EnrollmentStatus> RESTORABLE = EnumSet.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);

    private final EnrollmentRepository enrollments;
    private final FreeSlotRepository slots;
    private final ProjectRepository projects;
    private final UserAccountRepository users;
    private final SubmissionRepository submissions;
    private final BillingService billing;

    public EnrollmentService(EnrollmentRepository enrollments, FreeSlotRepository slots, ProjectRepository projects,
                             UserAccountRepository users, SubmissionRepository submissions, BillingService billing) {
        this.enrollments = enrollments;
        this.slots = slots;
        this.projects = projects;
        this.users = users;
        this.submissions = submissions;
        this.billing = billing;
    }

    @Transactional
    public EnrollmentView enroll(UserAccount student, String projectPublicId) {
        users.lockById(student.getId()).orElseThrow(AppException::unauthorized);
        Project project = projects.findByPublicId(projectPublicId)
                .filter(item -> item.getStatus() == ProjectStatus.PUBLISHED)
                .orElseThrow(() -> AppException.notFound("Project not found."));
        var existing = enrollments.findByProjectIdAndStudentId(project.getId(), student.getId());
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        AccessGrantType grant;
        FreeAllowanceSlot slot = null;
        if (billing.hasActivePremium(student)) {
            grant = AccessGrantType.PREMIUM;
        } else {
            slot = slots.findFirstByStudentIdAndStatusOrderBySlotIndexAsc(student.getId(), SlotStatus.AVAILABLE)
                    .orElseThrow(() -> AppException.paymentRequired(
                            "The first two projects are free. Further projects need Premium (€19/month or €199/year). A free place is not returned if you leave a project. It is returned only if the company withdraws the project before you submit."));
            grant = AccessGrantType.FREE_CREDIT;
        }
        ProjectEnrollment enrollment = new ProjectEnrollment();
        enrollment.setPublicId(Ids.uuid());
        enrollment.setProject(project);
        enrollment.setStudent(student);
        enrollment.setAccessGrantType(grant);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrolledAt(LocalDateTime.now(ZoneOffset.UTC));
        enrollment.setUpdatedAt(enrollment.getEnrolledAt());
        enrollments.save(enrollment);
        if (slot != null) {
            slot.setStatus(SlotStatus.CONSUMED);
            slot.setEnrollment(enrollment);
            slot.setUpdatedAt(enrollment.getEnrolledAt());
        }
        return toView(enrollment);
    }

    @Transactional(readOnly = true)
    public EnrollmentView getOwn(UserAccount student, String enrollmentPublicId) {
        return toView(own(student, enrollmentPublicId));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentView> list(UserAccount student) {
        return enrollments.findByStudentIdOrderByEnrolledAtDesc(student.getId()).stream().map(this::toView).toList();
    }

    @Transactional
    public void restoreWithdrawn(Project project) {
        for (ProjectEnrollment enrollment : enrollments.findByProjectId(project.getId())) {
            if (!RESTORABLE.contains(enrollment.getStatus())) {
                continue;
            }
            if (submissions.countSubmitted(enrollment.getId()) > 0) {
                continue;
            }
            if (enrollment.getAccessGrantType() == AccessGrantType.FREE_CREDIT) {
                slots.findByEnrollmentId(enrollment.getId()).ifPresent(slot -> {
                    slot.setStatus(SlotStatus.AVAILABLE);
                    slot.setEnrollment(null);
                    slot.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                });
            }
            enrollment.setStatus(EnrollmentStatus.CANCELLED);
            enrollment.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    public ProjectEnrollment own(UserAccount student, String enrollmentPublicId) {
        ProjectEnrollment enrollment = enrollments.findByPublicId(enrollmentPublicId)
                .orElseThrow(() -> AppException.notFound("Enrollment not found."));
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw AppException.notFound("Enrollment not found.");
        }
        return enrollment;
    }

    public EnrollmentView toView(ProjectEnrollment enrollment) {
        Project project = enrollment.getProject();
        return new EnrollmentView(enrollment.getPublicId(), project.getPublicId(), project.getTitle(),
                project.getCompany().getName(), enrollment.getStatus().name(), enrollment.getAccessGrantType().name(),
                enrollment.getEnrolledAt().toString());
    }

    public void touch(ProjectEnrollment enrollment, EnrollmentStatus status) {
        enrollment.setStatus(status);
        enrollment.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    }

    public boolean hasSubmitted(ProjectEnrollment enrollment) {
        return submissions.countSubmitted(enrollment.getId()) > 0 || submissions.findByEnrollmentIdOrderByRevisionNumberDesc(enrollment.getId())
                .stream().anyMatch(item -> item.getStatus() == SubmissionStatus.SUBMITTED);
    }

    public record EnrollmentView(String id, String projectId, String projectTitle, String companyName, String status,
                                 String accessGrantType, String enrolledAt) {}
}
