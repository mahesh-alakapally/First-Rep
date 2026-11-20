package com.firstrep.assessment;

import com.firstrep.assessment.AssessmentModels.AssessmentDraft;
import com.firstrep.assessment.AssessmentModels.AssessmentRequest;
import com.firstrep.assessment.AssessmentModels.CriterionDraft;
import com.firstrep.assessment.AssessmentModels.CriterionView;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.AssessmentStatus;
import com.firstrep.common.Kinds.EnrollmentStatus;
import com.firstrep.config.AppProperties;
import com.firstrep.enrollment.EnrollmentService;
import com.firstrep.notification.NotificationService;
import com.firstrep.project.RubricCriterion;
import com.firstrep.project.RubricCriterionRepository;
import com.firstrep.submission.Submission;
import com.firstrep.submission.SubmissionRepository;
import com.firstrep.submission.SubmissionService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentProcessor {
    private static final Logger log = LoggerFactory.getLogger(AssessmentProcessor.class);
    private final SubmissionRepository submissions;
    private final SubmissionService submissionService;
    private final AssessmentJobRepository jobs;
    private final AssessmentRepository assessments;
    private final CriterionResultRepository results;
    private final RubricCriterionRepository criteria;
    private final AssessmentProvider provider;
    private final EnrollmentService enrollments;
    private final NotificationService notifications;
    private final AppProperties properties;

    public AssessmentProcessor(SubmissionRepository submissions, @Lazy SubmissionService submissionService, AssessmentJobRepository jobs,
                               AssessmentRepository assessments, CriterionResultRepository results, RubricCriterionRepository criteria,
                               AssessmentProvider provider, EnrollmentService enrollments, NotificationService notifications,
                               AppProperties properties) {
        this.submissions = submissions;
        this.submissionService = submissionService;
        this.jobs = jobs;
        this.assessments = assessments;
        this.results = results;
        this.criteria = criteria;
        this.provider = provider;
        this.enrollments = enrollments;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Transactional
    public void process(String submissionPublicId) {
        Submission submission = submissions.findByPublicId(submissionPublicId).orElse(null);
        if (submission == null || submission.getRubricVersion() == null) {
            return;
        }
        AssessmentJob job = jobs.findBySubmissionId(submission.getId()).orElse(null);
        if (job == null) {
            return;
        }
        if (job.getStatus() == AssessmentStatus.AI_DRAFT_READY || job.getStatus() == AssessmentStatus.RELEASED
                || job.getStatus() == AssessmentStatus.HUMAN_REVIEW || assessments.findBySubmissionId(submission.getId()).isPresent()) {
            return;
        }
        job.setStatus(AssessmentStatus.PROCESSING);
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        List<RubricCriterion> rubric = criteria.findByRubricVersionIdOrderBySortOrderAsc(submission.getRubricVersion().getId());
        List<CriterionView> views = rubric.stream()
                .map(item -> new CriterionView(item.getPublicId(), item.getTitle(), item.getDescription(), item.getMaxScore()))
                .toList();
        String evidence = submissionService.evidenceText(submission);
        if (evidence.length() > properties.getAi().getMaxEvidenceChars()) {
            evidence = evidence.substring(0, properties.getAi().getMaxEvidenceChars());
        }
        AssessmentDraft draft = provider.assess(new AssessmentRequest(submission.getEnrollment().getProject().getTitle(),
                "v" + submission.getRubricVersion().getVersionNumber(), views, evidence));
        AssessmentValidator.validate(draft, views);
        Assessment assessment = new Assessment();
        assessment.setPublicId(Ids.uuid());
        assessment.setSubmission(submission);
        assessment.setRubricVersion(submission.getRubricVersion());
        assessment.setStatus(AssessmentStatus.AI_DRAFT_READY);
        assessment.setProvider(provider.name());
        assessment.setModelVersion(provider.model() == null || provider.model().isBlank() ? "unconfigured" : provider.model());
        assessment.setPromptVersion(properties.getAi().getPromptVersion());
        assessment.setSummary(draft.summary());
        assessment.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        assessments.save(assessment);
        for (CriterionDraft item : draft.criteria()) {
            CriterionResult result = new CriterionResult();
            result.setAssessment(assessment);
            result.setCriterionPublicId(item.criterionId());
            result.setScore(item.score());
            result.setMaxScore(item.maxScore());
            result.setEvidence(item.evidence());
            result.setStrengths(item.strengths());
            result.setGaps(item.gaps());
            result.setFeedback(item.feedback());
            result.setConfidence(item.confidence());
            result.setSafetyFlags(item.safetyFlags() == null ? "" : String.join(",", item.safetyFlags()));
            results.save(result);
        }
        job.setStatus(AssessmentStatus.AI_DRAFT_READY);
        job.setLastError(null);
        job.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        enrollments.touch(submission.getEnrollment(), EnrollmentStatus.REVIEW_PENDING);
        log.info("Assessment draft ready for submission {}", submission.getPublicId());
    }

    @Transactional
    public void markRetrying(String submissionPublicId, String error) {
        updateJob(submissionPublicId, AssessmentStatus.RETRYING, error);
    }

    @Transactional
    public void markManual(String submissionPublicId, String error) {
        updateJob(submissionPublicId, AssessmentStatus.MANUAL_REVIEW_REQUIRED, error);
        submissions.findByPublicId(submissionPublicId).ifPresent(submission -> {
            enrollments.touch(submission.getEnrollment(), EnrollmentStatus.REVIEW_PENDING);
            notifications.notify(submission.getEnrollment().getStudent(), "Assessment needs a reviewer",
                    "Your submission is saved. Automatic drafting did not finish, so a reviewer can take it from here.");
        });
    }

    private void updateJob(String submissionPublicId, AssessmentStatus status, String error) {
        submissions.findByPublicId(submissionPublicId).flatMap(submission -> jobs.findBySubmissionId(submission.getId())).ifPresent(job -> {
            job.setStatus(status);
            job.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
            job.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        });
    }
}
