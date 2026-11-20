package com.firstrep.company;

import com.firstrep.access.AccessGuard;
import com.firstrep.access.Role;
import com.firstrep.access.RoleRepository;
import com.firstrep.audit.AuditService;
import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.MembershipStatus;
import com.firstrep.config.AppProperties;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import com.firstrep.notification.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyTeamService {
    private static final Set<String> COMPANY_ROLES = Set.of("ORG_ADMIN", "RECRUITER", "REVIEWER");
    private final AccessGuard access;
    private final CompanyMembershipRepository memberships;
    private final CompanyInvitationRepository invitations;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final AppProperties properties;

    public CompanyTeamService(AccessGuard access, CompanyMembershipRepository memberships, CompanyInvitationRepository invitations,
                              UserAccountRepository users, RoleRepository roles, NotificationService notifications, AuditService audit,
                              CurrentUser currentUser, AppProperties properties) {
        this.access = access;
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
        this.roles = roles;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(String companyId) {
        var membership = access.requireCompany(companyId, "COMPANY_MANAGE");
        return memberships.findByCompanyId(membership.getCompany().getId()).stream().map(this::toView).toList();
    }

    @Transactional
    public java.util.Map<String, Object> invite(String companyId, String email, String roleName) {
        var membership = access.requireCompany(companyId, "COMPANY_MANAGE");
        String role = roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
        if (!COMPANY_ROLES.contains(role)) {
            throw AppException.bad("VALIDATION", "Role must be ORG_ADMIN, RECRUITER, or REVIEWER.");
        }
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        var existing = users.findByEmailIgnoreCase(normalised);
        if (existing.isPresent()) {
            addRole(membership.getCompany(), existing.get(), role);
            return java.util.Map.of("status", "ADDED");
        }
        String token = Ids.randomToken();
        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setPublicId(Ids.uuid());
        invitation.setCompany(membership.getCompany());
        invitation.setEmail(normalised);
        invitation.setRoleName(role);
        invitation.setTokenHash(Ids.sha256(token));
        invitation.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        invitation.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        invitations.save(invitation);
        String url = properties.getAppBaseUrl().replaceAll("/$", "") + "/invitations/accept?token=" + token;
        notifications.sendMail(normalised, "FirstRep company invitation", "Accept the invitation:\n" + url, false);
        audit.record(currentUser.require(), "INVITATION_SENT", "COMPANY", membership.getCompany().getPublicId(), membership.getCompany().getId(), role);
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", "INVITED");
        if (properties.getDev().isExposeEmailTokens()) {
            body.put("devAcceptUrl", url);
        }
        return body;
    }

    @Transactional
    public MemberView accept(UserAccount user, String token) {
        if (token == null || token.isBlank()) {
            throw AppException.bad("BAD_TOKEN", "That invitation is not valid.");
        }
        CompanyInvitation invitation = invitations.findByTokenHash(Ids.sha256(token))
                .orElseThrow(() -> AppException.bad("BAD_TOKEN", "That invitation is not valid."));
        if (invitation.getAcceptedAt() != null || invitation.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw AppException.bad("BAD_TOKEN", "That invitation is not valid.");
        }
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw AppException.forbidden("Sign in with the invited email.");
        }
        invitation.setAcceptedAt(LocalDateTime.now(ZoneOffset.UTC));
        return addRole(invitation.getCompany(), user, invitation.getRoleName());
    }

    @Transactional
    public MemberView updateMember(String companyId, String memberUserId, String roleName, String status) {
        var actor = access.requireCompany(companyId, "COMPANY_MANAGE");
        CompanyMembership membership = memberships.findByCompanyId(actor.getCompany().getId()).stream()
                .filter(item -> item.getUser().getPublicId().equals(memberUserId))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("Member not found."));
        if (status != null && !status.isBlank()) {
            membership.setStatus(MembershipStatus.valueOf(status));
        }
        if (roleName != null && !roleName.isBlank()) {
            String role = roleName.trim().toUpperCase(Locale.ROOT);
            if (!COMPANY_ROLES.contains(role)) {
                throw AppException.bad("VALIDATION", "Unknown company role.");
            }
            membership.getRoles().clear();
            membership.getRoles().add(requireRole(role));
        }
        audit.record(currentUser.require(), "MEMBER_UPDATED", "MEMBERSHIP", String.valueOf(membership.getId()), actor.getCompany().getId(), "Membership updated");
        return toView(membership);
    }

    private MemberView addRole(Company company, UserAccount user, String roleName) {
        CompanyMembership membership = memberships.findByCompanyIdAndUserId(company.getId(), user.getId()).orElseGet(() -> {
            CompanyMembership created = new CompanyMembership();
            created.setCompany(company);
            created.setUser(user);
            created.setStatus(MembershipStatus.ACTIVE);
            return memberships.save(created);
        });
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.getRoles().add(requireRole(roleName));
        return toView(membership);
    }

    private Role requireRole(String name) {
        return roles.findByName(name).orElseThrow(() -> AppException.bad("VALIDATION", "Unknown role."));
    }

    private MemberView toView(CompanyMembership membership) {
        return new MemberView(membership.getUser().getPublicId(), membership.getUser().getEmail(),
                membership.getStatus().name(), membership.getRoles().stream().map(Role::getName).sorted().toList());
    }

    public record MemberView(String userId, String email, String status, List<String> roles) {}
    public record InviteRequest(String email, String roleName) {}
    public record MemberUpdate(String roleName, String status) {}
    public record AcceptRequest(String token) {}
}
