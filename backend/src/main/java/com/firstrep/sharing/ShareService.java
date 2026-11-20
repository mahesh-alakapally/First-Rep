package com.firstrep.sharing;

import com.firstrep.access.AccessGuard;
import com.firstrep.assessment.AssessmentRepository;
import com.firstrep.audit.AuditService;
import com.firstrep.certificate.CertificateRepository;
import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.ShareStatus;
import com.firstrep.identity.StudentProfile;
import com.firstrep.identity.StudentProfileRepository;
import com.firstrep.identity.UserAccount;
import com.firstrep.skills.SkillsService;
import com.firstrep.storage.ObjectStorage;
import com.firstrep.submission.Submission;
import com.firstrep.submission.SubmissionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {
    public static final int MAX_DAYS = 90;
    private final ShareRepository shares;
    private final EvidenceItemRepository items;
    private final SubmissionRepository submissions;
    private final StudentProfileRepository profiles;
    private final AssessmentRepository assessments;
    private final CertificateRepository certificates;
    private final SkillsService skills;
    private final AccessGuard access;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final ObjectStorage storage;

    public ShareService(ShareRepository shares, EvidenceItemRepository items, SubmissionRepository submissions,
                        StudentProfileRepository profiles, AssessmentRepository assessments, CertificateRepository certificates,
                        SkillsService skills, AccessGuard access, CurrentUser currentUser, AuditService audit, ObjectStorage storage) {
        this.shares = shares;
        this.items = items;
        this.submissions = submissions;
        this.profiles = profiles;
        this.assessments = assessments;
        this.certificates = certificates;
        this.skills = skills;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.storage = storage;
    }

    @Transactional
    public ShareView share(UserAccount student, String submissionId, ShareRequest request) {
        Submission submission = submissions.findByPublicId(submissionId)
                .filter(item -> item.getEnrollment().getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> AppException.notFound("Submission not found."));
        if (request.purpose() == null || request.purpose().isBlank()) {
            throw AppException.bad("VALIDATION", "Say why you are sharing this.");
        }
        int days = request.expiresInDays() == null ? 30 : request.expiresInDays();
        if (days < 1 || days > MAX_DAYS) {
            throw AppException.bad("VALIDATION", "Choose an expiry between 1 and 90 days.");
        }
        CandidateShare share = new CandidateShare();
        share.setPublicId(Ids.uuid());
        share.setUser(student);
        share.setCompany(submission.getEnrollment().getProject().getCompany());
        share.setProject(submission.getEnrollment().getProject());
        share.setSubmission(submission);
        share.setPurpose(request.purpose().trim());
        share.setConsentVersion(com.firstrep.common.Kinds.CONSENT_VERSION);
        share.setConsentedAt(LocalDateTime.now(ZoneOffset.UTC));
        share.setExpiresAt(share.getConsentedAt().plusDays(days));
        share.setStatus(ShareStatus.ACTIVE);
        share.setIncludeProfile(request.includeProfile());
        share.setIncludeCv(request.includeCv());
        share.setIncludeSubmission(request.includeSubmission());
        share.setIncludeAssessment(request.includeAssessment());
        share.setIncludeSkills(request.includeSkills());
        share.setIncludeCertificate(request.includeCertificate());
        shares.save(share);
        addItem(share, "PROJECT", submission.getEnrollment().getProject().getPublicId());
        if (request.includeSubmission()) {
            addItem(share, "SUBMISSION", submission.getPublicId());
        }
        if (request.includeCertificate()) {
            certificates.findByUserIdAndProjectId(student.getId(), share.getProject().getId())
                    .ifPresent(certificate -> addItem(share, "CERTIFICATE", certificate.getPublicId()));
        }
        audit.record(student, "CONSENT_GRANTED", "SHARE", share.getPublicId(), share.getCompany().getId(), share.getPurpose());
        return toSummary(share);
    }

    @Transactional
    public ShareView revoke(UserAccount student, String shareId) {
        CandidateShare share = shares.findByPublicId(shareId)
                .filter(item -> item.getUser().getId().equals(student.getId()))
                .orElseThrow(() -> AppException.notFound("Share not found."));
        share.setStatus(ShareStatus.REVOKED);
        share.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        audit.record(student, "CONSENT_REVOKED", "SHARE", share.getPublicId(), share.getCompany().getId(), "Revoked");
        return toSummary(share);
    }

    @Transactional(readOnly = true)
    public List<ShareView> mine(UserAccount student) {
        return shares.findByUserIdOrderByConsentedAtDesc(student.getId()).stream().map(this::toSummary).toList();
    }

    @Transactional
    public List<ShareView> companyShares(String companyId) {
        var membership = access.requireCompany(companyId, "CANDIDATE_EVIDENCE_VIEW_CONSENTED");
        List<ShareView> views = new ArrayList<>();
        for (CandidateShare share : shares.findByCompanyIdOrderByConsentedAtDesc(membership.getCompany().getId())) {
            if (visible(share)) {
                views.add(toSummary(share));
            }
        }
        return views;
    }

    @Transactional
    public PackageView open(String companyId, String shareId) {
        var membership = access.requireCompany(companyId, "CANDIDATE_EVIDENCE_VIEW_CONSENTED");
        CandidateShare share = shares.findByPublicId(shareId)
                .filter(item -> item.getCompany().getId().equals(membership.getCompany().getId()))
                .filter(this::visible)
                .orElseThrow(() -> AppException.notFound("Shared evidence not found."));
        audit.record(currentUser.require(), "EVIDENCE_OPENED", "SHARE", share.getPublicId(), share.getCompany().getId(), "Recruiter opened package");
        StudentProfile profile = profiles.findByUserId(share.getUser().getId()).orElse(null);
        PackageView.ProfileSlice profileSlice = share.isIncludeProfile() && profile != null
                ? new PackageView.ProfileSlice(profile.getDisplayName(), profile.getHeadline(), profile.getEducation(), profile.getTargetRoles(), profile.getBio(), profile.getPortfolioUrl(), profile.getGithubUrl())
                : null;
        String cv = share.isIncludeCv() && profile != null ? profile.getCvFilename() : null;
        String submissionId = share.isIncludeSubmission() && share.getSubmission() != null ? share.getSubmission().getPublicId() : null;
        String assessmentSummary = null;
        if (share.isIncludeAssessment() && share.getSubmission() != null) {
            assessmentSummary = assessments.findBySubmissionId(share.getSubmission().getId())
                    .filter(item -> item.getStatus() == com.firstrep.common.Kinds.AssessmentStatus.RELEASED)
                    .map(item -> item.getSummary()).orElse(null);
        }
        PackageView.SkillsSlice skillsSlice = null;
        if (share.isIncludeSkills()) {
            var attempt = skills.latest(share.getUser(), share.getProject().getId());
            if (attempt != null && attempt.getStatus() == com.firstrep.common.Kinds.AttemptStatus.SUBMITTED) {
                skillsSlice = new PackageView.SkillsSlice(attempt.getScore(), attempt.getMaxScore(), attempt.getPassed());
            }
        }
        String certificateCode = null;
        if (share.isIncludeCertificate()) {
            certificateCode = certificates.findByUserIdAndProjectId(share.getUser().getId(), share.getProject().getId())
                    .map(item -> item.getVerificationCode()).orElse(null);
        }
        return new PackageView(toSummary(share), profileSlice, cv, submissionId, assessmentSummary, skillsSlice, certificateCode);
    }

    @Transactional
    public byte[] cv(String companyId, String shareId) {
        CandidateShare share = consented(companyId, shareId);
        if (!share.isIncludeCv()) {
            throw AppException.notFound("Shared evidence not found.");
        }
        StudentProfile profile = profiles.findByUserId(share.getUser().getId()).orElseThrow(() -> AppException.notFound("Shared evidence not found."));
        if (profile.getCvObjectKey() == null) {
            throw AppException.notFound("Shared evidence not found.");
        }
        audit.record(currentUser.require(), "EVIDENCE_CV_OPENED", "SHARE", share.getPublicId(), share.getCompany().getId(), "CV opened");
        return storage.get(profile.getCvObjectKey());
    }

    private CandidateShare consented(String companyId, String shareId) {
        var membership = access.requireCompany(companyId, "CANDIDATE_EVIDENCE_VIEW_CONSENTED");
        return shares.findByPublicId(shareId)
                .filter(item -> item.getCompany().getId().equals(membership.getCompany().getId()))
                .filter(this::visible)
                .orElseThrow(() -> AppException.notFound("Shared evidence not found."));
    }

    private boolean visible(CandidateShare share) {
        if (share.getStatus() == ShareStatus.REVOKED || share.getRevokedAt() != null) {
            return false;
        }
        if (share.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            return false;
        }
        return share.getStatus() == ShareStatus.ACTIVE;
    }

    private void addItem(CandidateShare share, String type, String ref) {
        EvidenceItem item = new EvidenceItem();
        item.setShare(share);
        item.setItemType(type);
        item.setItemRef(ref);
        items.save(item);
    }

    private ShareView toSummary(CandidateShare share) {
        return new ShareView(share.getPublicId(), share.getCompany().getPublicId(), share.getCompany().getName(),
                share.getProject().getTitle(), share.getPurpose(), share.getConsentVersion(), share.getStatus().name(),
                share.getConsentedAt().toString(), share.getExpiresAt().toString(),
                share.getRevokedAt() == null ? null : share.getRevokedAt().toString(),
                share.isIncludeProfile(), share.isIncludeCv(), share.isIncludeSubmission(), share.isIncludeAssessment(),
                share.isIncludeSkills(), share.isIncludeCertificate());
    }

    public record ShareRequest(String purpose, Integer expiresInDays, boolean includeProfile, boolean includeCv, boolean includeSubmission,
                               boolean includeAssessment, boolean includeSkills, boolean includeCertificate) {}
    public record ShareView(String id, String companyId, String companyName, String projectTitle, String purpose, String consentVersion,
                            String status, String consentedAt, String expiresAt, String revokedAt, boolean includeProfile, boolean includeCv,
                            boolean includeSubmission, boolean includeAssessment, boolean includeSkills, boolean includeCertificate) {}
    public record PackageView(ShareView share, ProfileSlice profile, String cvFilename, String submissionId, String assessmentSummary,
                              SkillsSlice skills, String certificateCode) {
        public record ProfileSlice(String displayName, String headline, String education, String targetRoles, String bio, String portfolioUrl, String githubUrl) {}
        public record SkillsSlice(Integer score, Integer maxScore, Boolean passed) {}
    }
}
