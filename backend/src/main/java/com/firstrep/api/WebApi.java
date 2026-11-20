package com.firstrep.api;

import com.firstrep.admin.AdminService;
import com.firstrep.assessment.ReviewService;
import com.firstrep.certificate.CertificateService;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.PageResponse;
import com.firstrep.company.CompanyTeamService;
import com.firstrep.config.SecurityConfig;
import com.firstrep.config.SecurityConfig.CookieWriter;
import com.firstrep.entitlement.BillingService;
import com.firstrep.enrollment.EnrollmentService;
import com.firstrep.identity.IdentityService;
import com.firstrep.identity.UserAccount;
import com.firstrep.notification.NotificationRepository;
import com.firstrep.project.CatalogService;
import com.firstrep.sharing.ShareService;
import com.firstrep.skills.SkillsService;
import com.firstrep.submission.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class WebApi {
    private final IdentityService identity;
    private final CatalogService catalog;
    private final CompanyTeamService teams;
    private final BillingService billing;
    private final EnrollmentService enrollments;
    private final SubmissionService submissions;
    private final ReviewService reviews;
    private final SkillsService skills;
    private final CertificateService certificates;
    private final ShareService shares;
    private final AdminService admin;
    private final NotificationRepository notifications;
    private final CurrentUser currentUser;
    private final CookieWriter cookies;

    public WebApi(IdentityService identity, CatalogService catalog, CompanyTeamService teams, BillingService billing,
                  EnrollmentService enrollments, SubmissionService submissions, ReviewService reviews, SkillsService skills,
                  CertificateService certificates, ShareService shares, AdminService admin, NotificationRepository notifications,
                  CurrentUser currentUser, CookieWriter cookies) {
        this.identity = identity;
        this.catalog = catalog;
        this.teams = teams;
        this.billing = billing;
        this.enrollments = enrollments;
        this.submissions = submissions;
        this.reviews = reviews;
        this.skills = skills;
        this.certificates = certificates;
        this.shares = shares;
        this.admin = admin;
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.cookies = cookies;
    }

    @GetMapping("/api/v1/auth/csrf")
    public Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
        return Map.of("csrfToken", cookies.ensureCsrf(request, response));
    }

    @PostMapping("/api/v1/auth/register")
    public Map<String, Object> register(@Valid @RequestBody IdentityService.Credentials request) {
        return identity.register(request.email(), request.password(), request.displayName());
    }

    @PostMapping("/api/v1/auth/login")
    public IdentityService.Me login(@Valid @RequestBody IdentityService.LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        var session = identity.login(request.email(), request.password());
        cookies.access(response, session.access());
        cookies.refresh(response, session.refresh());
        cookies.ensureCsrf(http, response);
        return identity.me(session.user());
    }

    @PostMapping("/api/v1/auth/refresh")
    public IdentityService.Me refresh(HttpServletRequest request, HttpServletResponse response) {
        var session = identity.refresh(SecurityConfig.readCookie(request, SecurityConfig.REFRESH));
        cookies.access(response, session.access());
        cookies.refresh(response, session.refresh());
        return identity.me(session.user());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        identity.logout(SecurityConfig.readCookie(request, SecurityConfig.REFRESH));
        cookies.clearAuth(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/verify-email")
    public Map<String, Object> verify(@Valid @RequestBody IdentityService.TokenRequest request) {
        return identity.verify(request.token());
    }

    @PostMapping("/api/v1/auth/forgot-password")
    public Map<String, Object> forgot(@Valid @RequestBody IdentityService.ForgotRequest request) {
        return identity.forgot(request.email());
    }

    @PostMapping("/api/v1/auth/reset-password")
    public Map<String, Object> reset(@Valid @RequestBody IdentityService.ResetRequest request) {
        return identity.reset(request.token(), request.password());
    }

    @GetMapping("/api/v1/me")
    public IdentityService.Me me() {
        return identity.me(user());
    }

    @PostMapping("/api/v1/me/password")
    public Map<String, String> password(@Valid @RequestBody IdentityService.PasswordChange request) {
        identity.changePassword(user(), request.currentPassword(), request.newPassword());
        return Map.of("message", "Password updated.");
    }

    @GetMapping("/api/v1/me/export")
    public Map<String, Object> export() {
        return identity.export(user());
    }

    @PostMapping("/api/v1/me/deletion-requests")
    public Map<String, String> deletion() {
        return identity.requestDeletion(user());
    }

    @GetMapping("/api/v1/students/me")
    public IdentityService.ProfileView profile() {
        return identity.profile(user());
    }

    @PutMapping("/api/v1/students/me")
    public IdentityService.ProfileView updateProfile(@Valid @RequestBody IdentityService.ProfileUpdate request) {
        return identity.updateProfile(user(), request);
    }

    @PostMapping(value = "/api/v1/students/me/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentityService.ProfileView cv(@RequestParam("file") MultipartFile file) throws Exception {
        return identity.storeCv(user(), file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping("/api/v1/students/me/cv-upload-url")
    public Map<String, String> cvUrl(@RequestBody Map<String, String> body) {
        return identity.cvUploadUrl(user(), body.get("filename"));
    }

    @PostMapping("/api/v1/students/me/cv-complete")
    public IdentityService.ProfileView cvComplete(@RequestBody SubmissionService.CompleteUpload request) {
        return identity.completeCv(user(), request.objectKey(), request.filename());
    }

    @GetMapping("/api/v1/students/me/enrollments")
    public java.util.List<EnrollmentService.EnrollmentView> myEnrollments() {
        return enrollments.list(user());
    }

    @GetMapping({"/api/v1/students/me/certificates", "/api/v1/me/certificates"})
    public java.util.List<CertificateService.CertificateView> myCertificates() {
        return certificates.mine(user());
    }

    @GetMapping("/api/v1/me/entitlements")
    public BillingService.Entitlements entitlements() {
        return billing.entitlements(user());
    }

    @GetMapping("/api/v1/skills")
    public java.util.List<CatalogService.SkillView> skills() {
        return catalog.listSkills();
    }

    @GetMapping("/api/v1/companies/public")
    public java.util.List<CatalogService.CompanyView> publicCompanies() {
        return catalog.publicCompanies();
    }

    @PostMapping("/api/v1/companies")
    public CatalogService.CompanyView createCompany(@Valid @RequestBody CatalogService.CompanyWrite request) {
        return catalog.createCompany(user(), request);
    }

    @GetMapping("/api/v1/companies/{companyId}")
    public CatalogService.CompanyView company(@PathVariable String companyId) {
        return catalog.viewForMember(companyId);
    }

    @PatchMapping("/api/v1/companies/{companyId}")
    public CatalogService.CompanyView updateCompany(@PathVariable String companyId, @Valid @RequestBody CatalogService.CompanyWrite request) {
        return catalog.updateCompany(companyId, request);
    }

    @GetMapping("/api/v1/companies/{companyId}/members")
    public java.util.List<CompanyTeamService.MemberView> members(@PathVariable String companyId) {
        return teams.members(companyId);
    }

    @PostMapping("/api/v1/companies/{companyId}/invitations")
    public Map<String, Object> invite(@PathVariable String companyId, @RequestBody CompanyTeamService.InviteRequest request) {
        return teams.invite(companyId, request.email(), request.roleName());
    }

    @PatchMapping("/api/v1/companies/{companyId}/members/{memberId}")
    public CompanyTeamService.MemberView updateMember(@PathVariable String companyId, @PathVariable String memberId, @RequestBody CompanyTeamService.MemberUpdate request) {
        return teams.updateMember(companyId, memberId, request.roleName(), request.status());
    }

    @PostMapping("/api/v1/invitations/accept")
    public CompanyTeamService.MemberView accept(@RequestBody CompanyTeamService.AcceptRequest request) {
        return teams.accept(user(), request.token());
    }

    @GetMapping("/api/v1/projects")
    public PageResponse<CatalogService.ProjectSummary> projects(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                                                @RequestParam(required = false) String difficulty, @RequestParam(required = false) String companyId,
                                                                @RequestParam(required = false) String skill, @RequestParam(required = false) Integer effortMax,
                                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                                                @RequestParam(defaultValue = "newest") String sort) {
        return catalog.search(q, role, difficulty, companyId, skill, effortMax, page, size, sort);
    }

    @GetMapping("/api/v1/projects/{projectId}")
    public CatalogService.ProjectDetail project(@PathVariable String projectId) {
        return catalog.publicDetail(projectId);
    }

    @GetMapping("/api/v1/companies/{companyId}/projects")
    public java.util.List<CatalogService.ProjectDetail> companyProjects(@PathVariable String companyId) {
        return catalog.companyProjects(companyId);
    }

    @PostMapping("/api/v1/companies/{companyId}/projects")
    public CatalogService.ProjectDetail createProject(@PathVariable String companyId, @Valid @RequestBody CatalogService.ProjectWrite request) {
        return catalog.createProject(companyId, request);
    }

    @PatchMapping("/api/v1/companies/{companyId}/projects/{projectId}")
    public CatalogService.ProjectDetail updateProject(@PathVariable String companyId, @PathVariable String projectId, @Valid @RequestBody CatalogService.ProjectWrite request) {
        return catalog.updateProject(companyId, projectId, request);
    }

    @PostMapping("/api/v1/companies/{companyId}/projects/{projectId}/submit-for-approval")
    public CatalogService.ProjectDetail submitProject(@PathVariable String companyId, @PathVariable String projectId) {
        return catalog.submitForApproval(companyId, projectId);
    }

    @GetMapping("/api/v1/admin/projects")
    public java.util.List<CatalogService.ProjectSummary> moderationQueue() {
        return catalog.moderationQueue();
    }

    @GetMapping("/api/v1/admin/projects/{projectId}")
    public CatalogService.ProjectDetail adminProject(@PathVariable String projectId) {
        return catalog.adminDetail(projectId);
    }

    @PostMapping("/api/v1/admin/projects/{projectId}/{action}")
    public CatalogService.ProjectDetail moderate(@PathVariable String projectId, @PathVariable String action, @RequestBody(required = false) CatalogService.ModerationRequest request) {
        return catalog.moderate(projectId, action, request == null ? null : request.note());
    }

    @PostMapping("/api/v1/projects/{projectId}/enroll")
    public EnrollmentService.EnrollmentView enroll(@PathVariable String projectId) {
        return enrollments.enroll(user(), projectId);
    }

    @GetMapping("/api/v1/enrollments/{enrollmentId}")
    public EnrollmentService.EnrollmentView enrollment(@PathVariable String enrollmentId) {
        return enrollments.getOwn(user(), enrollmentId);
    }

    @GetMapping("/api/v1/enrollments/{enrollmentId}/submissions")
    public java.util.List<SubmissionService.SubmissionView> enrollmentSubmissions(@PathVariable String enrollmentId) {
        return submissions.listForEnrollment(user(), enrollmentId);
    }

    @PostMapping("/api/v1/enrollments/{enrollmentId}/submissions")
    public SubmissionService.SubmissionView draft(@PathVariable String enrollmentId, @RequestBody(required = false) SubmissionService.NotesRequest request) {
        return submissions.openDraft(user(), enrollmentId, request == null ? null : request.notes());
    }

    @PostMapping("/api/v1/submissions/{submissionId}/links")
    public SubmissionService.SubmissionView link(@PathVariable String submissionId, @RequestBody SubmissionService.LinkRequest request) {
        return submissions.addLink(user(), submissionId, request.url());
    }

    @PostMapping(value = "/api/v1/submissions/{submissionId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubmissionService.SubmissionView asset(@PathVariable String submissionId, @RequestParam("file") MultipartFile file) throws Exception {
        return submissions.addFile(user(), submissionId, file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping("/api/v1/submissions/{submissionId}/assets/upload-url")
    public Map<String, String> uploadUrl(@PathVariable String submissionId, @RequestBody Map<String, String> body) {
        return submissions.uploadUrl(user(), submissionId, body.get("filename"));
    }

    @PostMapping("/api/v1/submissions/{submissionId}/assets/complete")
    public SubmissionService.SubmissionView complete(@PathVariable String submissionId, @RequestBody SubmissionService.CompleteUpload request) {
        return submissions.completeUpload(user(), submissionId, request.objectKey(), request.filename());
    }

    @PostMapping("/api/v1/submissions/{submissionId}/submit")
    public SubmissionService.SubmissionView submit(@PathVariable String submissionId) {
        return submissions.submit(user(), submissionId);
    }

    @GetMapping("/api/v1/submissions/{submissionId}")
    public SubmissionService.SubmissionView submission(@PathVariable String submissionId) {
        return submissions.get(submissionId);
    }

    @GetMapping("/api/v1/submissions/{submissionId}/assets/{assetId}")
    public ResponseEntity<byte[]> assetDownload(@PathVariable String submissionId, @PathVariable String assetId) {
        var file = submissions.download(submissionId, assetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.body());
    }

    @GetMapping("/api/v1/companies/{companyId}/review-queue")
    public java.util.List<ReviewService.QueueItem> queue(@PathVariable String companyId) {
        return reviews.queue(companyId);
    }

    @PostMapping("/api/v1/submissions/{submissionId}/human-review")
    public SubmissionService.SubmissionView review(@PathVariable String submissionId, @RequestBody ReviewService.DecisionRequest request) {
        return reviews.decide(submissionId, request.decision(), request.notes());
    }

    @PostMapping("/api/v1/submissions/{submissionId}/appeals")
    public ReviewService.AppealView appeal(@PathVariable String submissionId, @RequestBody ReviewService.AppealRequest request) {
        return reviews.appeal(user(), submissionId, request.reason());
    }

    @GetMapping("/api/v1/projects/{projectId}/skills-round")
    public SkillsService.RoundView skillsRound(@PathVariable String projectId) {
        return skills.roundForProject(user(), projectId);
    }

    @PostMapping("/api/v1/skills-rounds/{roundId}/attempts")
    public SkillsService.AttemptView startAttempt(@PathVariable String roundId) {
        return skills.start(user(), roundId);
    }

    @PostMapping("/api/v1/skills-round-attempts/{attemptId}/submit")
    public SkillsService.AttemptView submitAttempt(@PathVariable String attemptId, @RequestBody SkillsService.SubmitAttempt request) {
        return skills.submit(user(), attemptId, request.answers());
    }

    @GetMapping("/api/v1/certificates/{code}/verify")
    public CertificateService.PublicCertificate verifyCertificate(@PathVariable String code) {
        return certificates.verify(code);
    }

    @GetMapping("/api/v1/certificates/{certificateId}/download")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable String certificateId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(certificates.download(user(), certificateId));
    }

    @PostMapping("/api/v1/submissions/{submissionId}/share-with-company")
    public ShareService.ShareView share(@PathVariable String submissionId, @RequestBody ShareService.ShareRequest request) {
        return shares.share(user(), submissionId, request);
    }

    @GetMapping("/api/v1/me/shares")
    public java.util.List<ShareService.ShareView> myShares() {
        return shares.mine(user());
    }

    @DeleteMapping("/api/v1/shares/{shareId}")
    public ShareService.ShareView revoke(@PathVariable String shareId) {
        return shares.revoke(user(), shareId);
    }

    @GetMapping("/api/v1/companies/{companyId}/shared-candidates")
    public java.util.List<ShareService.ShareView> shared(@PathVariable String companyId) {
        return shares.companyShares(companyId);
    }

    @GetMapping("/api/v1/companies/{companyId}/shares/{shareId}")
    public ShareService.PackageView openShare(@PathVariable String companyId, @PathVariable String shareId) {
        return shares.open(companyId, shareId);
    }

    @GetMapping("/api/v1/companies/{companyId}/shares/{shareId}/cv")
    public ResponseEntity<byte[]> shareCv(@PathVariable String companyId, @PathVariable String shareId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(shares.cv(companyId, shareId));
    }

    @GetMapping("/api/v1/billing/plans")
    public java.util.List<BillingService.PlanView> plans() {
        return billing.listPlans();
    }

    @PostMapping("/api/v1/billing/checkout-session")
    public Map<String, Object> checkout(@RequestBody BillingService.CheckoutRequest request) {
        return billing.checkout(user(), request.planCode());
    }

    @PostMapping("/api/v1/billing/simulator/confirm")
    public BillingService.Entitlements confirm(@RequestBody BillingService.ConfirmRequest request) {
        return billing.confirmSimulator(user(), request.checkoutId());
    }

    @PostMapping("/api/v1/billing/customer-portal-session")
    public Map<String, String> portal() {
        return billing.portal(user());
    }

    @PostMapping("/api/v1/billing/cancel")
    public BillingService.Entitlements cancel() {
        return billing.cancel(user());
    }

    @GetMapping("/api/v1/billing/history")
    public java.util.List<BillingService.PaymentView> history() {
        return billing.history(user());
    }

    @PostMapping("/api/v1/webhooks/stripe")
    public Map<String, String> stripe(HttpServletRequest request) throws Exception {
        String payload = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        billing.handleStripe(payload, request.getHeader("Stripe-Signature"));
        return Map.of("received", "true");
    }

    @GetMapping("/api/v1/notifications")
    public java.util.List<Map<String, Object>> notifications() {
        return notifications.findTop30ByUserIdOrderByCreatedAtDesc(user().getId()).stream().map(item -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", item.getPublicId());
            row.put("title", item.getTitle());
            row.put("body", item.getBody());
            row.put("readAt", item.getReadAt() == null ? null : item.getReadAt().toString());
            row.put("createdAt", item.getCreatedAt().toString());
            return row;
        }).toList();
    }

    @PostMapping("/api/v1/notifications/{id}/read")
    public Map<String, String> readNotification(@PathVariable String id) {
        var note = notifications.findByPublicId(id).filter(item -> item.getUser().getId().equals(user().getId()))
                .orElseThrow(() -> com.firstrep.common.AppException.notFound("Notification not found."));
        note.setReadAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        notifications.save(note);
        return Map.of("status", "READ");
    }

    @PostMapping("/api/v1/pilot-interest")
    public Map<String, String> interest(@RequestBody AdminService.InterestRequest request) {
        return admin.interest(request.name(), request.email(), request.organisation(), request.message());
    }

    @GetMapping("/api/v1/admin/users")
    public PageResponse<AdminService.UserView> adminUsers(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.users(q, page, size);
    }

    @PostMapping("/api/v1/admin/users/{userId}/status")
    public AdminService.UserView status(@PathVariable String userId, @RequestBody AdminService.StatusRequest request) {
        return admin.setStatus(userId, request.status());
    }

    @GetMapping("/api/v1/admin/data-requests")
    public java.util.List<AdminService.DataRequestView> dataRequests() {
        return admin.dataRequests();
    }

    @PostMapping("/api/v1/admin/data-requests/{id}/complete")
    public Map<String, String> completeDeletion(@PathVariable String id) {
        admin.completeDeletion(id);
        return Map.of("status", "COMPLETED");
    }

    @GetMapping("/api/v1/admin/audit-events")
    public PageResponse<AdminService.AuditView> audit(@RequestParam(required = false) String action, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return admin.audit(action, page, size);
    }

    @GetMapping("/api/v1/admin/reports/pilot-metrics")
    public Map<String, Object> metrics() {
        return admin.pilotMetrics();
    }

    @GetMapping("/api/v1/admin/appeals")
    public java.util.List<ReviewService.AppealView> appeals() {
        return reviews.openAppeals();
    }

    @PostMapping("/api/v1/admin/appeals/{id}/resolve")
    public ReviewService.AppealView resolve(@PathVariable String id, @RequestBody ReviewService.ResolveRequest request) {
        return reviews.resolve(id, request.status(), request.note());
    }

    @GetMapping("/api/v1/admin/subscriptions")
    public java.util.List<Map<String, Object>> adminSubscriptions() {
        return admin.subscriptions();
    }

    @GetMapping("/api/v1/admin/certificates")
    public java.util.List<Map<String, String>> adminCertificates() {
        return admin.certificates();
    }

    @GetMapping("/api/v1/admin/interest")
    public java.util.List<Map<String, String>> adminInterest() {
        return admin.interestList();
    }

    private UserAccount user() {
        return currentUser.require();
    }
}
