package com.firstrep.identity;

import com.firstrep.access.Role;
import com.firstrep.access.RoleRepository;
import com.firstrep.admin.DataRequest;
import com.firstrep.admin.DataRequestRepository;
import com.firstrep.audit.AuditService;
import com.firstrep.common.AppException;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.AccountStatus;
import com.firstrep.common.Kinds.DataRequestStatus;
import com.firstrep.common.Kinds.DataRequestType;
import com.firstrep.common.Kinds.MembershipStatus;
import com.firstrep.common.Kinds.SlotStatus;
import com.firstrep.common.Kinds.TokenPurpose;
import com.firstrep.common.UploadInspector;
import com.firstrep.company.CompanyMembershipRepository;
import com.firstrep.config.AppProperties;
import com.firstrep.config.SecurityConfig.TokenService;
import com.firstrep.enrollment.FreeAllowanceSlot;
import com.firstrep.enrollment.FreeSlotRepository;
import com.firstrep.notification.NotificationService;
import com.firstrep.project.Skill;
import com.firstrep.project.SkillRepository;
import com.firstrep.storage.ObjectStorage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final UserAccountRepository users;
    private final EmailTokenRepository emailTokens;
    private final RefreshTokenRepository refreshTokens;
    private final StudentProfileRepository profiles;
    private final RoleRepository roles;
    private final FreeSlotRepository slots;
    private final CompanyMembershipRepository memberships;
    private final SkillRepository skills;
    private final DataRequestRepository dataRequests;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ObjectStorage storage;
    private final AppProperties properties;

    public IdentityService(UserAccountRepository users, EmailTokenRepository emailTokens, RefreshTokenRepository refreshTokens,
                           StudentProfileRepository profiles, RoleRepository roles, FreeSlotRepository slots,
                           CompanyMembershipRepository memberships, SkillRepository skills, DataRequestRepository dataRequests,
                           PasswordEncoder passwordEncoder, TokenService tokenService, NotificationService notifications,
                           AuditService audit, ObjectStorage storage, AppProperties properties) {
        this.users = users;
        this.emailTokens = emailTokens;
        this.refreshTokens = refreshTokens;
        this.profiles = profiles;
        this.roles = roles;
        this.slots = slots;
        this.memberships = memberships;
        this.skills = skills;
        this.dataRequests = dataRequests;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.notifications = notifications;
        this.audit = audit;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Object> register(String email, String password, String displayName) {
        String normalised = normalise(email);
        checkPassword(password);
        if (displayName == null || displayName.isBlank() || displayName.length() > 160) {
            throw AppException.bad("VALIDATION", "Enter a display name.");
        }
        if (users.findByEmailIgnoreCase(normalised).isPresent()) {
            throw AppException.conflict("EMAIL_IN_USE", "An account with that email already exists.");
        }
        UserAccount user = new UserAccount();
        user.setPublicId(Ids.uuid());
        user.setEmail(normalised);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        user.getPlatformRoles().add(role("STUDENT"));
        users.save(user);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setDisplayName(displayName.trim());
        profiles.save(profile);
        ensureSlots(user);
        String url = issueEmailToken(user, TokenPurpose.VERIFY_EMAIL, "/verify-email?token=");
        notifications.sendMail(user.getEmail(), "Verify your FirstRep email",
                "Open this link to verify your email:\n" + url, true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Check your email to verify the account before signing in.");
        if (properties.getDev().isExposeEmailTokens()) {
            body.put("devVerificationUrl", url);
        }
        return body;
    }

    @Transactional
    public Session login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(normalise(email)).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException(org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is not valid.");
        }
        if (user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(org.springframework.http.HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Verify your email before signing in.");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AppException(org.springframework.http.HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "This account cannot sign in.");
        }
        return issueSession(user);
    }

    @Transactional
    public Map<String, Object> verify(String token) {
        EmailToken emailToken = consume(token, TokenPurpose.VERIFY_EMAIL);
        UserAccount user = emailToken.getUser();
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        return Map.of("message", "Email verified. You can sign in.");
    }

    @Transactional
    public Map<String, Object> forgot(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "If an account exists for that email, a reset link is on its way.");
        users.findByEmailIgnoreCase(normalise(email)).ifPresent(user -> {
            if (user.getAccountStatus() == AccountStatus.DELETED) {
                return;
            }
            String url = issueEmailToken(user, TokenPurpose.PASSWORD_RESET, "/reset-password?token=");
            notifications.sendMail(user.getEmail(), "Reset your FirstRep password", "Reset link:\n" + url, false);
            if (properties.getDev().isExposeEmailTokens()) {
                body.put("devResetUrl", url);
            }
        });
        return body;
    }

    @Transactional
    public Map<String, Object> reset(String token, String password) {
        checkPassword(password);
        EmailToken emailToken = consume(token, TokenPurpose.PASSWORD_RESET);
        UserAccount user = emailToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        refreshTokens.findByUserId(user.getId()).forEach(item -> {
            if (item.getRevokedAt() == null) {
                item.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            }
        });
        return Map.of("message", "Password updated. Sign in with the new password.");
    }

    @Transactional
    public Session refresh(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw AppException.unauthorized();
        }
        RefreshToken current = refreshTokens.findByTokenHash(Ids.sha256(rawRefresh)).orElseThrow(AppException::unauthorized);
        if (current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId());
            throw AppException.unauthorized();
        }
        if (current.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw AppException.unauthorized();
        }
        UserAccount user = current.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw AppException.unauthorized();
        }
        current.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        String raw = Ids.randomToken();
        RefreshToken next = new RefreshToken();
        next.setUser(user);
        next.setTokenHash(Ids.sha256(raw));
        next.setFamilyId(current.getFamilyId());
        next.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(properties.getSecurity().getRefreshTtl()));
        next.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        refreshTokens.save(next);
        return new Session(user, tokenService.access(user.getPublicId()), raw);
    }

    @Transactional
    public void logout(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Ids.sha256(rawRefresh)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            }
        });
    }

    @Transactional
    public void changePassword(UserAccount user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw AppException.bad("BAD_PASSWORD", "Current password does not match.");
        }
        checkPassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        audit.record(user, "PASSWORD_CHANGED", "USER", user.getPublicId(), null, "Password changed");
    }

    @Transactional(readOnly = true)
    public Me me(UserAccount user) {
        user = users.findById(user.getId()).orElseThrow(AppException::unauthorized);
        StudentProfile profile = profiles.findByUserId(user.getId()).orElse(null);
        List<String> platformRoles = user.getPlatformRoles().stream().map(Role::getName).sorted().toList();
        List<MembershipView> views = memberships.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE).stream()
                .map(membership -> new MembershipView(
                        membership.getCompany().getPublicId(),
                        membership.getCompany().getName(),
                        membership.getRoles().stream().map(Role::getName).sorted().toList()))
                .toList();
        return new Me(user.getPublicId(), user.getEmail(), user.getAccountStatus().name(), platformRoles, views,
                profile == null ? null : profile.getDisplayName());
    }

    @Transactional(readOnly = true)
    public ProfileView profile(UserAccount user) {
        return toProfile(requireProfile(user));
    }

    @Transactional
    public ProfileView updateProfile(UserAccount user, ProfileUpdate update) {
        StudentProfile profile = requireProfile(user);
        if (update.displayName() == null || update.displayName().isBlank()) {
            throw AppException.bad("VALIDATION", "Display name is required.");
        }
        profile.setDisplayName(trim(update.displayName(), 160));
        profile.setHeadline(trim(update.headline(), 240));
        profile.setEducation(trim(update.education(), 500));
        profile.setTargetRoles(trim(update.targetRoles(), 500));
        profile.setBio(trim(update.bio(), 4000));
        profile.setPortfolioUrl(trim(update.portfolioUrl(), 500));
        profile.setGithubUrl(trim(update.githubUrl(), 500));
        profile.setShareProfileInPackages(update.shareProfileInPackages());
        profile.getSkills().clear();
        if (update.skillNames() != null) {
            for (String name : update.skillNames()) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                Skill skill = skills.findByNameIgnoreCase(name.trim())
                        .orElseThrow(() -> AppException.bad("UNKNOWN_SKILL", "Unknown skill: " + name));
                profile.getSkills().add(skill);
            }
        }
        return toProfile(profile);
    }

    @Transactional
    public ProfileView storeCv(UserAccount user, String filename, byte[] bytes) {
        var inspected = UploadInspector.inspect(filename, bytes);
        String key = "cvs/" + user.getPublicId() + "/" + Ids.uuid() + "-" + inspected.filename();
        storage.put(key, bytes, inspected.contentType());
        StudentProfile profile = requireProfile(user);
        profile.setCvObjectKey(key);
        profile.setCvFilename(inspected.filename());
        return toProfile(profile);
    }

    @Transactional
    public Map<String, String> cvUploadUrl(UserAccount user, String filename) {
        String safe = filename == null ? "cv.pdf" : filename.replaceAll("[^A-Za-z0-9._-]", "-");
        String key = "cvs/" + user.getPublicId() + "/" + Ids.uuid() + "-" + safe;
        String url = storage.presignPut(key, "application/octet-stream", java.time.Duration.ofMinutes(10));
        return Map.of("objectKey", key, "uploadUrl", url);
    }

    @Transactional
    public ProfileView completeCv(UserAccount user, String objectKey, String filename) {
        if (objectKey == null || !objectKey.startsWith("cvs/" + user.getPublicId() + "/")) {
            throw AppException.bad("FILE_REJECTED", "That upload does not belong to this account.");
        }
        byte[] bytes = storage.get(objectKey);
        var inspected = UploadInspector.inspect(filename, bytes);
        StudentProfile profile = requireProfile(user);
        profile.setCvObjectKey(objectKey);
        profile.setCvFilename(inspected.filename());
        return toProfile(profile);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UserAccount user) {
        StudentProfile profile = profiles.findByUserId(user.getId()).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", me(user));
        body.put("profile", profile == null ? null : toProfile(profile));
        body.put("dataRequests", dataRequests.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(item -> Map.of("id", item.getPublicId(), "type", item.getRequestType().name(), "status", item.getStatus().name()))
                .toList());
        return body;
    }

    @Transactional
    public Map<String, String> requestDeletion(UserAccount user) {
        DataRequest request = new DataRequest();
        request.setPublicId(Ids.uuid());
        request.setUser(user);
        request.setRequestType(DataRequestType.DELETION);
        request.setStatus(DataRequestStatus.OPEN);
        request.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        dataRequests.save(request);
        audit.record(user, "DELETION_REQUESTED", "USER", user.getPublicId(), null, "Deletion requested");
        return Map.of("id", request.getPublicId(), "status", request.getStatus().name());
    }

    @Transactional
    public UserAccount createActiveUser(String email, String password, String displayName, String roleName, boolean studentSlots) {
        UserAccount user = new UserAccount();
        user.setPublicId(Ids.uuid());
        user.setEmail(normalise(email));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (roleName != null) {
            user.getPlatformRoles().add(role(roleName));
        }
        users.save(user);
        if (studentSlots) {
            StudentProfile profile = new StudentProfile();
            profile.setUser(user);
            profile.setDisplayName(displayName);
            profiles.save(profile);
            ensureSlots(user);
        }
        return user;
    }

    public void ensureSlots(UserAccount user) {
        if (!slots.findByStudentIdOrderBySlotIndexAsc(user.getId()).isEmpty()) {
            return;
        }
        for (int i = 1; i <= properties.getFreeAllowance(); i++) {
            FreeAllowanceSlot slot = new FreeAllowanceSlot();
            slot.setStudent(user);
            slot.setSlotIndex(i);
            slot.setStatus(SlotStatus.AVAILABLE);
            slot.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            slot.setUpdatedAt(slot.getCreatedAt());
            slots.save(slot);
        }
    }

    private Session issueSession(UserAccount user) {
        String raw = Ids.randomToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(Ids.sha256(raw));
        refresh.setFamilyId(Ids.uuid());
        refresh.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(properties.getSecurity().getRefreshTtl()));
        refresh.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        refreshTokens.save(refresh);
        return new Session(user, tokenService.access(user.getPublicId()), raw);
    }

    private String issueEmailToken(UserAccount user, TokenPurpose purpose, String path) {
        String raw = Ids.randomToken();
        EmailToken token = new EmailToken();
        token.setUser(user);
        token.setTokenHash(Ids.sha256(raw));
        token.setPurpose(purpose);
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        token.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        emailTokens.save(token);
        return properties.getAppBaseUrl().replaceAll("/$", "") + path + raw;
    }

    private EmailToken consume(String raw, TokenPurpose purpose) {
        if (raw == null || raw.isBlank()) {
            throw AppException.bad("BAD_TOKEN", "That link is not valid.");
        }
        EmailToken token = emailTokens.findFirstByTokenHashAndPurposeAndUsedAtIsNull(Ids.sha256(raw), purpose)
                .orElseThrow(() -> AppException.bad("BAD_TOKEN", "That link is not valid."));
        if (token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw AppException.bad("BAD_TOKEN", "That link has expired.");
        }
        token.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        return token;
    }

    private void revokeFamily(String familyId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        refreshTokens.findByFamilyId(familyId).forEach(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
            }
        });
    }

    private StudentProfile requireProfile(UserAccount user) {
        return profiles.findByUserId(user.getId()).orElseThrow(() -> AppException.notFound("Student profile not found."));
    }

    private ProfileView toProfile(StudentProfile profile) {
        return new ProfileView(profile.getDisplayName(), profile.getHeadline(), profile.getEducation(), profile.getTargetRoles(),
                profile.getBio(), profile.getPortfolioUrl(), profile.getGithubUrl(), profile.getCvFilename(),
                profile.isShareProfileInPackages(), profile.getSkills().stream().map(Skill::getName).sorted().toList());
    }

    private Role role(String name) {
        return roles.findByName(name).orElseThrow(() -> new IllegalStateException("Missing role " + name));
    }

    public static void checkPassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 200
                || password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw AppException.bad("WEAK_PASSWORD", "Use at least 10 characters with a letter and a number.");
        }
    }

    private static String normalise(String email) {
        if (email == null) {
            throw AppException.bad("VALIDATION", "Email is required.");
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public record Session(UserAccount user, String access, String refresh) {}
    public record Me(String id, String email, String status, List<String> platformRoles, List<MembershipView> memberships, String displayName) {}
    public record MembershipView(String companyId, String companyName, List<String> roles) {}
    public record ProfileView(String displayName, String headline, String education, String targetRoles, String bio,
                              String portfolioUrl, String githubUrl, String cvFilename, boolean shareProfileInPackages,
                              List<String> skillNames) {}
    public record ProfileUpdate(@NotBlank @Size(max = 160) String displayName, @Size(max = 240) String headline,
                                @Size(max = 500) String education, @Size(max = 500) String targetRoles,
                                @Size(max = 4000) String bio, @Size(max = 500) String portfolioUrl,
                                @Size(max = 500) String githubUrl, boolean shareProfileInPackages, List<String> skillNames) {}
    public record Credentials(@Email @NotBlank String email, @NotBlank String password, @NotBlank @Size(max = 160) String displayName) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record TokenRequest(@NotBlank String token) {}
    public record ResetRequest(@NotBlank String token, @NotBlank String password) {}
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record ForgotRequest(@Email @NotBlank String email) {}
}
