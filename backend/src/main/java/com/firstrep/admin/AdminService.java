package com.firstrep.admin;

import com.firstrep.access.AccessGuard;
import com.firstrep.audit.AuditEventRepository;
import com.firstrep.audit.AuditService;
import com.firstrep.certificate.CertificateRepository;
import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Kinds.AccountStatus;
import com.firstrep.common.Kinds.DataRequestStatus;
import com.firstrep.common.PageResponse;
import com.firstrep.company.CompanyRepository;
import com.firstrep.entitlement.PaymentRepository;
import com.firstrep.entitlement.SubscriptionRepository;
import com.firstrep.identity.StudentProfileRepository;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import com.firstrep.project.ProjectRepository;
import com.firstrep.common.Kinds.ProjectStatus;
import com.firstrep.enrollment.EnrollmentRepository;
import com.firstrep.common.Kinds.AccessGrantType;
import com.firstrep.submission.SubmissionRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final AccessGuard access;
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final AuditEventRepository auditEvents;
    private final AuditService audit;
    private final DataRequestRepository dataRequests;
    private final PilotInterestRepository interest;
    private final ProjectRepository projects;
    private final CompanyRepository companies;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final CertificateRepository certificates;
    private final CurrentUser currentUser;

    public AdminService(AccessGuard access, UserAccountRepository users, StudentProfileRepository profiles, AuditEventRepository auditEvents,
                        AuditService audit, DataRequestRepository dataRequests, PilotInterestRepository interest, ProjectRepository projects,
                        CompanyRepository companies, EnrollmentRepository enrollments, SubmissionRepository submissions,
                        SubscriptionRepository subscriptions, PaymentRepository payments, CertificateRepository certificates, CurrentUser currentUser) {
        this.access = access;
        this.users = users;
        this.profiles = profiles;
        this.auditEvents = auditEvents;
        this.audit = audit;
        this.dataRequests = dataRequests;
        this.interest = interest;
        this.projects = projects;
        this.companies = companies;
        this.enrollments = enrollments;
        this.submissions = submissions;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.certificates = certificates;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> users(String q, int page, int size) {
        access.requirePlatform("USER_MANAGE");
        var result = users.search(q == null ? "" : q, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
        return new PageResponse<>(result.getContent().stream().map(this::toUser).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public UserView setStatus(String userId, String status) {
        access.requirePlatform("USER_MANAGE");
        UserAccount user = users.findByPublicId(userId).orElseThrow(() -> AppException.notFound("User not found."));
        user.setAccountStatus(AccountStatus.valueOf(status));
        audit.record(currentUser.require(), "USER_STATUS", "USER", user.getPublicId(), null, status);
        return toUser(user);
    }

    @Transactional
    public void completeDeletion(String requestId) {
        access.requirePlatform("USER_MANAGE");
        DataRequest request = dataRequests.findByPublicId(requestId).orElseThrow(() -> AppException.notFound("Request not found."));
        UserAccount user = request.getUser();
        user.setEmail("deleted+" + user.getId() + "@invalid.example");
        user.setAccountStatus(AccountStatus.DELETED);
        user.setPasswordHash("deleted");
        profiles.findByUserId(user.getId()).ifPresent(profile -> {
            profile.setDisplayName("Deleted student");
            profile.setHeadline(null);
            profile.setEducation(null);
            profile.setTargetRoles(null);
            profile.setBio(null);
            profile.setPortfolioUrl(null);
            profile.setGithubUrl(null);
            profile.setCvObjectKey(null);
            profile.setCvFilename(null);
        });
        request.setStatus(DataRequestStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        audit.record(currentUser.require(), "DELETION_COMPLETED", "USER", user.getPublicId(), null, "PII anonymised; financial rows kept");
    }

    @Transactional(readOnly = true)
    public List<DataRequestView> dataRequests() {
        access.requirePlatform("USER_MANAGE");
        return dataRequests.findByStatusOrderByCreatedAtAsc(DataRequestStatus.OPEN).stream()
                .map(item -> new DataRequestView(item.getPublicId(), item.getUser().getPublicId(), item.getRequestType().name(), item.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> audit(String action, int page, int size) {
        access.requirePlatform("REPORT_VIEW");
        var result = auditEvents.search(action == null || action.isBlank() ? null : action, null, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
        return new PageResponse<>(result.getContent().stream()
                .map(event -> new AuditView(event.getId(), event.getActor() == null ? null : event.getActor().getPublicId(), event.getAction(),
                        event.getResourceType(), event.getResourceId(), event.getDetail(), event.getCreatedAt().toString()))
                .toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pilotMetrics() {
        access.requirePlatform("REPORT_VIEW");
        var allProjects = projects.findAll();
        long published = allProjects.stream().filter(project -> project.getStatus() == ProjectStatus.PUBLISHED).count();
        Map<Long, Long> publishedByCompany = new HashMap<>();
        allProjects.stream().filter(project -> project.getStatus() == ProjectStatus.PUBLISHED)
                .forEach(project -> publishedByCompany.merge(project.getCompany().getId(), 1L, Long::sum));
        long companiesWithOne = publishedByCompany.size();
        long companiesWithTwo = publishedByCompany.values().stream().filter(count -> count >= 2).count();
        var allEnrollments = enrollments.findAll();
        long free = allEnrollments.stream().filter(item -> item.getAccessGrantType() == AccessGrantType.FREE_CREDIT).count();
        long premium = allEnrollments.stream().filter(item -> item.getAccessGrantType() == AccessGrantType.PREMIUM).count();
        long completed = allEnrollments.stream().filter(item -> item.getStatus().name().equals("COMPLETED")).count();
        long activeSubs = subscriptions.findAll().stream().filter(item -> "ACTIVE".equals(item.getStatus().name()) || "CANCEL_AT_PERIOD_END".equals(item.getStatus().name())).count();
        Map<String, Object> body = new HashMap<>();
        body.put("companies", companies.count());
        body.put("companiesWithPublishedProject", companiesWithOne);
        body.put("companiesWithSecondProject", companiesWithTwo);
        body.put("publishedProjects", published);
        body.put("projectsByStatus", allProjects.stream().collect(java.util.stream.Collectors.groupingBy(project -> project.getStatus().name(), java.util.stream.Collectors.counting())));
        body.put("enrollments", allEnrollments.size());
        body.put("freeEnrollments", free);
        body.put("premiumEnrollments", premium);
        body.put("completedEnrollments", completed);
        body.put("submissions", submissions.count());
        body.put("activeSubscriptions", activeSubs);
        body.put("payments", payments.count());
        body.put("certificates", certificates.count());
        body.put("openDeletionRequests", dataRequests.findByStatusOrderByCreatedAtAsc(DataRequestStatus.OPEN).size());
        return body;
    }

    @Transactional
    public Map<String, String> interest(String name, String email, String organisation, String message) {
        if (name == null || email == null || organisation == null || name.isBlank() || email.isBlank() || organisation.isBlank()) {
            throw AppException.bad("VALIDATION", "Name, email, and organisation are required.");
        }
        PilotInterest row = new PilotInterest();
        row.setPublicId(com.firstrep.common.Ids.uuid());
        row.setName(name.trim());
        row.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
        row.setOrganisation(organisation.trim());
        row.setMessage(message);
        row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        interest.save(row);
        return Map.of("id", row.getPublicId(), "message", "Thanks. The pilot team can see this note.");
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> interestList() {
        access.requirePlatform("REPORT_VIEW");
        return interest.findTop100ByOrderByCreatedAtDesc().stream()
                .map(item -> Map.of("id", item.getPublicId(), "name", item.getName(), "email", item.getEmail(), "organisation", item.getOrganisation(),
                        "message", item.getMessage() == null ? "" : item.getMessage(), "createdAt", item.getCreatedAt().toString()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> subscriptions() {
        access.requirePlatform("SUBSCRIPTION_MANAGE");
        return subscriptions.findAll().stream().map(item -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", item.getPublicId());
            row.put("userId", item.getUser().getPublicId());
            row.put("plan", item.getPlan().getCode());
            row.put("status", item.getStatus().name());
            row.put("provider", item.getProvider());
            row.put("currentPeriodEnd", item.getCurrentPeriodEnd() == null ? null : item.getCurrentPeriodEnd().toString());
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> certificates() {
        access.requirePlatform("REPORT_VIEW");
        return certificates.findAll().stream()
                .map(item -> Map.of("id", item.getPublicId(), "code", item.getVerificationCode(), "project", item.getProjectTitle(), "student", item.getStudentDisplayName(), "issuedAt", item.getIssuedAt().toString()))
                .toList();
    }

    private UserView toUser(UserAccount user) {
        return new UserView(user.getPublicId(), user.getEmail(), user.getAccountStatus().name(),
                user.getPlatformRoles().stream().map(role -> role.getName()).sorted().toList());
    }

    public record UserView(String id, String email, String status, List<String> platformRoles) {}
    public record DataRequestView(String id, String userId, String type, String status) {}
    public record AuditView(Long id, String actorId, String action, String resourceType, String resourceId, String detail, String createdAt) {}
    public record StatusRequest(String status) {}
    public record InterestRequest(String name, String email, String organisation, String message) {}
}
