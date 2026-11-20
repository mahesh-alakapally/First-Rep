package com.firstrep.assessment;

import com.firstrep.access.AccessGuard;
import com.firstrep.audit.AuditService;
import com.firstrep.certificate.CertificateService;
import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.AppealStatus;
import com.firstrep.common.Kinds.AssessmentStatus;
import com.firstrep.common.Kinds.EnrollmentStatus;
import com.firstrep.common.Kinds.ReviewDecision;
import com.firstrep.company.CompanyMembershipRepository;
import com.firstrep.enrollment.EnrollmentService;
import com.firstrep.identity.UserAccount;
import com.firstrep.notification.NotificationService;
import com.firstrep.submission.Submission;
import com.firstrep.submission.SubmissionRepository;
import com.firstrep.submission.SubmissionService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final SubmissionRepository submissions;
    private final SubmissionService submissionService;
    private final AssessmentRepository assessments;
    private final AssessmentJobRepository jobs;
    private final HumanReviewRepository reviews;
    private final AppealRepository appeals;
    private final EnrollmentService enrollments;
    private final CertificateService certificates;
    private final AccessGuard access;
    private final CompanyMembershipRepository memberships;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final NotificationService notifications;

    public ReviewService(SubmissionRepository submissions, SubmissionService submissionService, AssessmentRepository assessments,
                         AssessmentJobRepository jobs, HumanReviewRepository reviews, AppealRepository appeals,
                         EnrollmentService enrollments, CertificateService certificates, AccessGuard access,
                         CompanyMembershipRepository memberships, CurrentUser currentUser, AuditService audit,
                         NotificationService notifications) {
        this.submissions = submissions;
        this.submissionService = submissionService;
        this.assessments = assessments;
        this.jobs = jobs;
        this.reviews = reviews;
        this.appeals = appeals;
        this.enrollments = enrollments;
        this.certificates = certificates;
        this.access = access;
        this.memberships = memberships;
        this.currentUser = currentUser;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<QueueItem> queue(String companyId) {
        var membership = access.requireCompany(companyId, "SUBMISSION_REVIEW_ASSIGNED");
        List<QueueItem> items = new ArrayList<>();
        for (Submission submission : submissions.findSubmittedForCompany(membership.getCompany().getId())) {
            var assessment = assessments.findBySubmissionId(submission.getId()).orElse(null);
            var job = jobs.findBySubmissionId(submission.getId()).orElse(null);
            boolean waiting = assessment == null || assessment.getStatus() != AssessmentStatus.RELEASED;
            if (!waiting) {
                continue;
            }
            items.add(new QueueItem(submission.getPublicId(), submission.getEnrollment().getProject().getTitle(),
                    submission.getEnrollment().getStudent().getPublicId(),
                    job == null ? null : job.getStatus().name(),
                    assessment == null ? null : assessment.getStatus().name(),
                    submission.getSubmittedAt() == null ? null : submission.getSubmittedAt().toString()));
        }
        return items;
    }

    @Transactional
    public SubmissionService.SubmissionView decide(String submissionId, String decision, String notes) {
        Submission submission = submissions.findByPublicId(submissionId).orElseThrow(() -> AppException.notFound("Submission not found."));
        String companyId = submission.getEnrollment().getProject().getCompany().getPublicId();
        access.requireCompany(companyId, "SUBMISSION_REVIEW_ASSIGNED");
        ReviewDecision parsed;
        try {
            parsed = ReviewDecision.valueOf(decision);
        } catch (Exception ex) {
            throw AppException.bad("VALIDATION", "Decision must be APPROVE, REQUEST_CHANGES, or REJECT.");
        }
        if (notes == null || notes.isBlank()) {
            throw AppException.bad("NOTE_REQUIRED", "Add a note for the student.");
        }
        Assessment assessment = assessments.findBySubmissionId(submission.getId()).orElse(null);
        AssessmentJob job = jobs.findBySubmissionId(submission.getId()).orElse(null);
        if (assessment == null || (assessment.getStatus() != AssessmentStatus.AI_DRAFT_READY
                && assessment.getStatus() != AssessmentStatus.HUMAN_REVIEW
                && (job == null || job.getStatus() != AssessmentStatus.MANUAL_REVIEW_REQUIRED))) {
            if (job != null && job.getStatus() == AssessmentStatus.MANUAL_REVIEW_REQUIRED && assessment == null) {
                assessment = new Assessment();
                assessment.setPublicId(Ids.uuid());
                assessment.setSubmission(submission);
                assessment.setRubricVersion(submission.getRubricVersion());
                assessment.setStatus(AssessmentStatus.HUMAN_REVIEW);
                assessment.setProvider("manual");
                assessment.setModelVersion("none");
                assessment.setPromptVersion("none");
                assessment.setSummary("Automatic draft was unavailable. The reviewer decided from the evidence.");
                assessment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
                assessments.save(assessment);
            } else if (assessment == null) {
                throw AppException.bad("NOT_READY", "The assessment draft is not ready yet.");
            } else if (assessment.getStatus() == AssessmentStatus.RELEASED) {
                throw AppException.bad("ALREADY_REVIEWED", "This assessment has already been released.");
            }
        }
        HumanReview review = new HumanReview();
        review.setAssessment(assessment);
        review.setReviewer(currentUser.require());
        review.setDecision(parsed);
        review.setNotes(notes.trim());
        review.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        reviews.save(review);
        assessment.setStatus(AssessmentStatus.RELEASED);
        assessment.setReleasedAt(review.getCreatedAt());
        if (job != null) {
            job.setStatus(AssessmentStatus.RELEASED);
            job.setUpdatedAt(review.getCreatedAt());
        }
        UserAccount student = submission.getEnrollment().getStudent();
        if (parsed == ReviewDecision.APPROVE) {
            enrollments.touch(submission.getEnrollment(), EnrollmentStatus.APPROVED);
            certificates.issue(assessment);
            enrollments.touch(submission.getEnrollment(), EnrollmentStatus.COMPLETED);
            notifications.notify(student, "Project approved", "Feedback and a certificate are ready for " + submission.getEnrollment().getProject().getTitle() + ".");
        } else if (parsed == ReviewDecision.REQUEST_CHANGES) {
            enrollments.touch(submission.getEnrollment(), EnrollmentStatus.CHANGES_REQUESTED);
            notifications.notify(student, "Changes requested", "A reviewer asked for changes on " + submission.getEnrollment().getProject().getTitle() + ".");
        } else {
            enrollments.touch(submission.getEnrollment(), EnrollmentStatus.REJECTED);
            notifications.notify(student, "Assessment decision", "A reviewer recorded a decision on " + submission.getEnrollment().getProject().getTitle() + ". You can appeal.");
        }
        audit.record(currentUser.require(), "REVIEW_" + parsed.name(), "SUBMISSION", submission.getPublicId(),
                submission.getEnrollment().getProject().getCompany().getId(), "Human review");
        return submissionService.get(submissionId);
    }

    @Transactional
    public AppealView appeal(UserAccount student, String submissionId, String reason) {
        Submission submission = submissions.findByPublicId(submissionId).orElseThrow(() -> AppException.notFound("Submission not found."));
        if (!submission.getEnrollment().getStudent().getId().equals(student.getId())) {
            throw AppException.notFound("Submission not found.");
        }
        Assessment assessment = assessments.findBySubmissionId(submission.getId())
                .filter(item -> item.getStatus() == AssessmentStatus.RELEASED)
                .orElseThrow(() -> AppException.bad("NOT_READY", "You can appeal after feedback is released."));
        if (reason == null || reason.isBlank()) {
            throw AppException.bad("VALIDATION", "Describe the appeal.");
        }
        if (appeals.findFirstByAssessmentIdAndStatus(assessment.getId(), AppealStatus.OPEN).isPresent()) {
            throw AppException.conflict("APPEAL_OPEN", "An appeal is already open.");
        }
        Appeal appeal = new Appeal();
        appeal.setPublicId(Ids.uuid());
        appeal.setAssessment(assessment);
        appeal.setStudent(student);
        appeal.setReason(reason.trim());
        appeal.setStatus(AppealStatus.OPEN);
        appeal.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        appeals.save(appeal);
        audit.record(student, "APPEAL_OPENED", "APPEAL", appeal.getPublicId(), null, "Appeal opened");
        return toAppeal(appeal);
    }

    @Transactional(readOnly = true)
    public List<AppealView> openAppeals() {
        access.requirePlatform("PROJECT_MODERATE");
        return appeals.findByStatusOrderByCreatedAtAsc(AppealStatus.OPEN).stream().map(this::toAppeal).toList();
    }

    @Transactional
    public AppealView resolve(String appealId, String status, String note) {
        access.requirePlatform("PROJECT_MODERATE");
        Appeal appeal = appeals.findByPublicId(appealId).orElseThrow(() -> AppException.notFound("Appeal not found."));
        AppealStatus parsed = "UPHELD".equals(status) ? AppealStatus.UPHELD : AppealStatus.DENIED;
        if (!"UPHELD".equals(status) && !"DENIED".equals(status)) {
            throw AppException.bad("VALIDATION", "Resolution must be UPHELD or DENIED.");
        }
        appeal.setStatus(parsed);
        appeal.setResolutionNote(note);
        appeal.setResolvedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (parsed == AppealStatus.UPHELD) {
            appeal.getAssessment().setStatus(AssessmentStatus.HUMAN_REVIEW);
            appeal.getAssessment().setReleasedAt(null);
            jobs.findBySubmissionId(appeal.getAssessment().getSubmission().getId()).ifPresent(job -> job.setStatus(AssessmentStatus.HUMAN_REVIEW));
            enrollments.touch(appeal.getAssessment().getSubmission().getEnrollment(), EnrollmentStatus.REVIEW_PENDING);
        }
        audit.record(currentUser.require(), "APPEAL_" + parsed.name(), "APPEAL", appeal.getPublicId(), null, "Appeal resolved");
        notifications.notify(appeal.getStudent(), "Appeal update", "Your appeal was marked " + parsed.name() + ".");
        return toAppeal(appeal);
    }

    private AppealView toAppeal(Appeal appeal) {
        return new AppealView(appeal.getPublicId(), appeal.getAssessment().getSubmission().getPublicId(), appeal.getReason(),
                appeal.getStatus().name(), appeal.getResolutionNote(), appeal.getCreatedAt().toString());
    }

    public record QueueItem(String submissionId, String projectTitle, String studentId, String jobStatus, String assessmentStatus, String submittedAt) {}
    public record DecisionRequest(String decision, String notes) {}
    public record AppealRequest(String reason) {}
    public record AppealView(String id, String submissionId, String reason, String status, String resolutionNote, String createdAt) {}
    public record ResolveRequest(String status, String note) {}
}
