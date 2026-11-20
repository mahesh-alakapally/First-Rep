package com.firstrep.submission;

import com.firstrep.access.AccessGuard;
import com.firstrep.assessment.AssessmentJob;
import com.firstrep.assessment.AssessmentJobRepository;
import com.firstrep.assessment.AssessmentRepository;
import com.firstrep.assessment.CriterionResultRepository;
import com.firstrep.assessment.HumanReviewRepository;
import com.firstrep.assessment.OutboxEvent;
import com.firstrep.assessment.OutboxEventRepository;
import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.AssessmentStatus;
import com.firstrep.common.Kinds.EnrollmentStatus;
import com.firstrep.common.Kinds.OutboxStatus;
import com.firstrep.common.Kinds.RubricStatus;
import com.firstrep.common.Kinds.ScanStatus;
import com.firstrep.common.Kinds.SubmissionStatus;
import com.firstrep.common.UploadInspector;
import com.firstrep.common.UrlSafety;
import com.firstrep.company.CompanyMembershipRepository;
import com.firstrep.enrollment.EnrollmentService;
import com.firstrep.enrollment.ProjectEnrollment;
import com.firstrep.identity.UserAccount;
import com.firstrep.project.RubricVersionRepository;
import com.firstrep.storage.ObjectStorage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {
    private final SubmissionRepository submissions;
    private final SubmissionAssetRepository assets;
    private final SubmissionLinkRepository links;
    private final AssessmentJobRepository jobs;
    private final AssessmentRepository assessments;
    private final CriterionResultRepository results;
    private final HumanReviewRepository reviews;
    private final OutboxEventRepository outbox;
    private final RubricVersionRepository rubrics;
    private final EnrollmentService enrollments;
    private final AccessGuard access;
    private final CompanyMembershipRepository memberships;
    private final CurrentUser currentUser;
    private final ObjectStorage storage;

    public SubmissionService(SubmissionRepository submissions, SubmissionAssetRepository assets, SubmissionLinkRepository links,
                             AssessmentJobRepository jobs, AssessmentRepository assessments, CriterionResultRepository results,
                             HumanReviewRepository reviews, OutboxEventRepository outbox, RubricVersionRepository rubrics,
                             EnrollmentService enrollments, AccessGuard access, CompanyMembershipRepository memberships,
                             CurrentUser currentUser, ObjectStorage storage) {
        this.submissions = submissions;
        this.assets = assets;
        this.links = links;
        this.jobs = jobs;
        this.assessments = assessments;
        this.results = results;
        this.reviews = reviews;
        this.outbox = outbox;
        this.rubrics = rubrics;
        this.enrollments = enrollments;
        this.access = access;
        this.memberships = memberships;
        this.currentUser = currentUser;
        this.storage = storage;
    }

    @Transactional
    public SubmissionView openDraft(UserAccount student, String enrollmentId, String notes) {
        ProjectEnrollment enrollment = enrollments.own(student, enrollmentId);
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED || enrollment.getStatus() == EnrollmentStatus.COMPLETED
                || enrollment.getStatus() == EnrollmentStatus.REJECTED) {
            throw AppException.bad("BAD_STATE", "This enrollment cannot take a new draft.");
        }
        Submission draft = submissions.findFirstByEnrollmentIdAndStatus(enrollment.getId(), SubmissionStatus.DRAFT).orElseGet(() -> {
            Submission created = new Submission();
            created.setPublicId(Ids.uuid());
            created.setEnrollment(enrollment);
            created.setRevisionNumber(submissions.maxRevision(enrollment.getId()) + 1);
            created.setStatus(SubmissionStatus.DRAFT);
            created.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            created.setUpdatedAt(created.getCreatedAt());
            return submissions.save(created);
        });
        if (notes != null) {
            draft.setNotes(notes.length() > 8000 ? notes.substring(0, 8000) : notes);
            draft.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        if (enrollment.getStatus() == EnrollmentStatus.ENROLLED || enrollment.getStatus() == EnrollmentStatus.CHANGES_REQUESTED) {
            enrollments.touch(enrollment, EnrollmentStatus.IN_PROGRESS);
        }
        return toView(draft, true, false);
    }

    @Transactional
    public SubmissionView addLink(UserAccount student, String submissionId, String url) {
        Submission submission = ownDraft(student, submissionId);
        var checked = UrlSafety.check(url);
        SubmissionLink link = new SubmissionLink();
        link.setPublicId(Ids.uuid());
        link.setSubmission(submission);
        link.setUrl(checked.url());
        link.setKind(checked.kind());
        link.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        links.save(link);
        return toView(submission, true, false);
    }

    @Transactional
    public SubmissionView addFile(UserAccount student, String submissionId, String filename, byte[] bytes) {
        Submission submission = ownDraft(student, submissionId);
        var inspected = UploadInspector.inspect(filename, bytes);
        String key = "submissions/" + submission.getPublicId() + "/" + Ids.uuid() + "-" + inspected.filename();
        storage.put(key, bytes, inspected.contentType());
        saveAsset(submission, key, inspected.filename(), inspected.contentType(), bytes.length, ScanStatus.CLEAN);
        return toView(submission, true, false);
    }

    @Transactional
    public java.util.Map<String, String> uploadUrl(UserAccount student, String submissionId, String filename) {
        Submission submission = ownDraft(student, submissionId);
        String safe = filename == null ? "evidence.bin" : filename.replaceAll("[^A-Za-z0-9._-]", "-");
        String key = "submissions/" + submission.getPublicId() + "/" + Ids.uuid() + "-" + safe;
        return java.util.Map.of("objectKey", key, "uploadUrl", storage.presignPut(key, "application/octet-stream", Duration.ofMinutes(10)));
    }

    @Transactional
    public SubmissionView completeUpload(UserAccount student, String submissionId, String objectKey, String filename) {
        Submission submission = ownDraft(student, submissionId);
        if (objectKey == null || !objectKey.startsWith("submissions/" + submission.getPublicId() + "/")) {
            throw AppException.bad("FILE_REJECTED", "That upload does not belong to this submission.");
        }
        byte[] bytes = storage.get(objectKey);
        var inspected = UploadInspector.inspect(filename, bytes);
        saveAsset(submission, objectKey, inspected.filename(), inspected.contentType(), bytes.length, ScanStatus.CLEAN);
        return toView(submission, true, false);
    }

    @Transactional
    public SubmissionView submit(UserAccount student, String submissionId) {
        Submission submission = ownDraft(student, submissionId);
        var fileList = assets.findBySubmissionIdOrderByCreatedAtAsc(submission.getId());
        var linkList = links.findBySubmissionIdOrderByCreatedAtAsc(submission.getId());
        if (fileList.stream().noneMatch(file -> file.getScanStatus() == ScanStatus.CLEAN) && linkList.isEmpty()
                && (submission.getNotes() == null || submission.getNotes().isBlank())) {
            throw AppException.bad("EMPTY_SUBMISSION", "Add a file, a link, or a note before submitting.");
        }
        var rubric = rubrics.findFirstByProjectIdAndStatusOrderByVersionNumberDesc(submission.getEnrollment().getProject().getId(), RubricStatus.PUBLISHED)
                .orElseThrow(() -> AppException.bad("RUBRIC_MISSING", "This project does not have a published rubric yet."));
        submission.setRubricVersion(rubric);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        submission.setUpdatedAt(submission.getSubmittedAt());
        enrollments.touch(submission.getEnrollment(), EnrollmentStatus.SUBMITTED);
        AssessmentJob job = new AssessmentJob();
        job.setPublicId(Ids.uuid());
        job.setSubmission(submission);
        job.setStatus(AssessmentStatus.QUEUED);
        job.setAttemptCount(0);
        job.setIdempotencyKey("submission:" + submission.getId());
        job.setCreatedAt(submission.getSubmittedAt());
        job.setUpdatedAt(submission.getSubmittedAt());
        jobs.save(job);
        OutboxEvent event = new OutboxEvent();
        event.setEventType("assessment.requested");
        event.setAggregateType("SUBMISSION");
        event.setAggregateId(submission.getPublicId());
        event.setPayload("{\"submissionPublicId\":\"" + submission.getPublicId() + "\"}");
        event.setStatus(OutboxStatus.PENDING);
        event.setCreatedAt(submission.getSubmittedAt());
        outbox.save(event);
        return toView(submission, true, false);
    }

    @Transactional(readOnly = true)
    public SubmissionView get(String submissionId) {
        Submission submission = submissions.findByPublicId(submissionId).orElseThrow(() -> AppException.notFound("Submission not found."));
        UserAccount user = currentUser.require();
        boolean owner = submission.getEnrollment().getStudent().getId().equals(user.getId());
        boolean reviewer = isReviewer(user, submission);
        if (!owner && !reviewer) {
            throw AppException.notFound("Submission not found.");
        }
        return toView(submission, owner, reviewer);
    }

    @Transactional(readOnly = true)
    public List<SubmissionView> listForEnrollment(UserAccount student, String enrollmentId) {
        ProjectEnrollment enrollment = enrollments.own(student, enrollmentId);
        return submissions.findByEnrollmentIdOrderByRevisionNumberDesc(enrollment.getId()).stream()
                .map(item -> toView(item, true, false)).toList();
    }

    @Transactional(readOnly = true)
    public Download download(String submissionId, String assetId) {
        Submission submission = submissions.findByPublicId(submissionId).orElseThrow(() -> AppException.notFound("Submission not found."));
        UserAccount user = currentUser.require();
        if (!submission.getEnrollment().getStudent().getId().equals(user.getId()) && !isReviewer(user, submission)) {
            throw AppException.notFound("Submission not found.");
        }
        SubmissionAsset asset = assets.findByPublicId(assetId).filter(item -> item.getSubmission().getId().equals(submission.getId()))
                .orElseThrow(() -> AppException.notFound("File not found."));
        if (asset.getScanStatus() != ScanStatus.CLEAN) {
            throw AppException.bad("FILE_REJECTED", "That file did not pass the upload check.");
        }
        return new Download(asset.getOriginalFilename(), asset.getContentType(), storage.get(asset.getObjectKey()));
    }

    public String evidenceText(Submission submission) {
        StringBuilder builder = new StringBuilder();
        builder.append("UNTRUSTED_EVIDENCE\n");
        if (submission.getNotes() != null) {
            builder.append("Notes:\n").append(submission.getNotes()).append('\n');
        }
        for (SubmissionLink link : links.findBySubmissionIdOrderByCreatedAtAsc(submission.getId())) {
            builder.append("Link (").append(link.getKind()).append("): ").append(link.getUrl()).append('\n');
        }
        for (SubmissionAsset asset : assets.findBySubmissionIdOrderByCreatedAtAsc(submission.getId())) {
            builder.append("File: ").append(asset.getOriginalFilename()).append(" (").append(asset.getContentType()).append(", ")
                    .append(asset.getSizeBytes()).append(" bytes)\n");
            if (asset.getContentType().startsWith("text/") && asset.getScanStatus() == ScanStatus.CLEAN) {
                try {
                    String text = new String(storage.get(asset.getObjectKey()), java.nio.charset.StandardCharsets.UTF_8);
                    builder.append(text, 0, Math.min(text.length(), 4000)).append('\n');
                } catch (RuntimeException ignored) {
                    builder.append("File could not be read as text.\n");
                }
            }
        }
        String value = builder.toString();
        return value.length() > 12000 ? value.substring(0, 12000) : value;
    }

    private Submission ownDraft(UserAccount student, String submissionId) {
        Submission submission = submissions.findByPublicId(submissionId).orElseThrow(() -> AppException.notFound("Submission not found."));
        if (!submission.getEnrollment().getStudent().getId().equals(student.getId())) {
            throw AppException.notFound("Submission not found.");
        }
        if (submission.getStatus() != SubmissionStatus.DRAFT) {
            throw AppException.bad("BAD_STATE", "This revision has already been submitted.");
        }
        return submission;
    }

    private void saveAsset(Submission submission, String key, String filename, String contentType, long size, ScanStatus status) {
        SubmissionAsset asset = new SubmissionAsset();
        asset.setPublicId(Ids.uuid());
        asset.setSubmission(submission);
        asset.setObjectKey(key);
        asset.setOriginalFilename(filename);
        asset.setContentType(contentType);
        asset.setSizeBytes(size);
        asset.setScanStatus(status);
        asset.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        assets.save(asset);
    }

    private boolean isReviewer(UserAccount user, Submission submission) {
        Long companyId = submission.getEnrollment().getProject().getCompany().getId();
        return memberships.permissionCodes(user.getId(), companyId).contains("SUBMISSION_REVIEW_ASSIGNED");
    }

    private SubmissionView toView(Submission submission, boolean owner, boolean reviewer) {
        var assessment = assessments.findBySubmissionId(submission.getId()).orElse(null);
        var job = jobs.findBySubmissionId(submission.getId()).orElse(null);
        boolean released = assessment != null && assessment.getStatus() == AssessmentStatus.RELEASED;
        boolean showScores = reviewer || (owner && released);
        AssessmentView assessmentView = null;
        if (assessment != null) {
            List<CriterionScore> scores = showScores ? results.findByAssessmentIdOrderByIdAsc(assessment.getId()).stream()
                    .map(item -> new CriterionScore(item.getCriterionPublicId(), item.getScore(), item.getMaxScore(), item.getEvidence(),
                            item.getStrengths(), item.getGaps(), item.getFeedback(), item.getConfidence(), item.getSafetyFlags()))
                    .toList() : List.of();
            List<ReviewView> reviewViews = showScores ? reviews.findByAssessmentIdOrderByCreatedAtAsc(assessment.getId()).stream()
                    .map(item -> new ReviewView(item.getDecision().name(), item.getNotes(), item.getCreatedAt().toString())).toList() : List.of();
            assessmentView = new AssessmentView(assessment.getPublicId(), assessment.getStatus().name(), showScores ? assessment.getSummary() : null,
                    assessment.getProvider(), assessment.getModelVersion(), assessment.getPromptVersion(),
                    assessment.getReleasedAt() == null ? null : assessment.getReleasedAt().toString(), scores, reviewViews);
        }
        return new SubmissionView(
                submission.getPublicId(),
                submission.getEnrollment().getPublicId(),
                submission.getRevisionNumber(),
                submission.getStatus().name(),
                submission.getNotes(),
                submission.getSubmittedAt() == null ? null : submission.getSubmittedAt().toString(),
                job == null ? null : job.getStatus().name(),
                assets.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()).stream()
                        .map(item -> new AssetView(item.getPublicId(), item.getOriginalFilename(), item.getContentType(), item.getSizeBytes(), item.getScanStatus().name()))
                        .toList(),
                links.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()).stream()
                        .map(item -> new LinkView(item.getPublicId(), item.getUrl(), item.getKind().name())).toList(),
                assessmentView);
    }

    public record SubmissionView(String id, String enrollmentId, int revision, String status, String notes, String submittedAt,
                                 String jobStatus, List<AssetView> assets, List<LinkView> links, AssessmentView assessment) {}
    public record AssetView(String id, String filename, String contentType, long sizeBytes, String scanStatus) {}
    public record LinkView(String id, String url, String kind) {}
    public record AssessmentView(String id, String status, String summary, String provider, String modelVersion, String promptVersion,
                                 String releasedAt, List<CriterionScore> criteria, List<ReviewView> reviews) {}
    public record CriterionScore(String criterionId, int score, int maxScore, String evidence, String strengths, String gaps,
                                 String feedback, String confidence, String safetyFlags) {}
    public record ReviewView(String decision, String notes, String createdAt) {}
    public record Download(String filename, String contentType, byte[] body) {}
    public record LinkRequest(String url) {}
    public record NotesRequest(String notes) {}
    public record CompleteUpload(String objectKey, String filename) {}
}
