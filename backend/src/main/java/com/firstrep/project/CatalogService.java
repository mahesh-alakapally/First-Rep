package com.firstrep.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstrep.access.AccessGuard;
import com.firstrep.access.RoleRepository;
import com.firstrep.audit.AuditService;
import com.firstrep.common.AppException;
import com.firstrep.common.CurrentUser;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.Difficulty;
import com.firstrep.common.Kinds.ProjectStatus;
import com.firstrep.common.Kinds.QuestionType;
import com.firstrep.common.Kinds.RubricStatus;
import com.firstrep.common.PageResponse;
import com.firstrep.common.Kinds.MembershipStatus;
import com.firstrep.company.Company;
import com.firstrep.company.CompanyMembership;
import com.firstrep.company.CompanyMembershipRepository;
import com.firstrep.company.CompanyRepository;
import com.firstrep.enrollment.EnrollmentRepository;
import com.firstrep.enrollment.EnrollmentService;
import com.firstrep.identity.UserAccount;
import com.firstrep.skills.SkillsQuestion;
import com.firstrep.skills.SkillsQuestionRepository;
import com.firstrep.skills.SkillsRound;
import com.firstrep.skills.SkillsRoundRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final RoleRepository roles;
    private final ProjectRepository projects;
    private final ProjectDeliverableRepository deliverables;
    private final RubricVersionRepository rubrics;
    private final RubricCriterionRepository criteria;
    private final SkillRepository skills;
    private final SkillsRoundRepository rounds;
    private final SkillsQuestionRepository questions;
    private final EnrollmentRepository enrollments;
    private final EnrollmentService enrollmentService;
    private final AccessGuard access;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public CatalogService(CompanyRepository companies, CompanyMembershipRepository memberships, RoleRepository roles, ProjectRepository projects, ProjectDeliverableRepository deliverables,
                          RubricVersionRepository rubrics, RubricCriterionRepository criteria, SkillRepository skills,
                          SkillsRoundRepository rounds, SkillsQuestionRepository questions, EnrollmentRepository enrollments,
                          EnrollmentService enrollmentService, AccessGuard access, CurrentUser currentUser, AuditService audit,
                          ObjectMapper objectMapper) {
        this.companies = companies;
        this.memberships = memberships;
        this.roles = roles;
        this.projects = projects;
        this.deliverables = deliverables;
        this.rubrics = rubrics;
        this.criteria = criteria;
        this.skills = skills;
        this.rounds = rounds;
        this.questions = questions;
        this.enrollments = enrollments;
        this.enrollmentService = enrollmentService;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CompanyView createCompany(UserAccount user, CompanyWrite write) {
        Company company = new Company();
        company.setPublicId(Ids.uuid());
        company.setName(required(write.name(), 200, "Company name"));
        company.setSlug(uniqueSlug(Ids.slug(company.getName())));
        company.setDescription(clip(write.description(), 4000));
        company.setLocation(clip(write.location(), 200));
        company.setWebsite(clip(write.website(), 500));
        company.setStatus(com.firstrep.common.Kinds.CompanyStatus.ACTIVE);
        companies.save(company);
        CompanyMembership membership = new CompanyMembership();
        membership.setCompany(company);
        membership.setUser(user);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.getRoles().add(roles.findByName("ORG_ADMIN").orElseThrow());
        memberships.save(membership);
        audit.record(user, "COMPANY_CREATED", "COMPANY", company.getPublicId(), company.getId(), company.getName());
        return toCompany(company, List.of());
    }

    @Transactional(readOnly = true)
    public CompanyView company(String companyId) {
        CompanyMembership membership = access.requireCompany(companyId, "COMPANY_MANAGE");
        if (!membership.getCompany().getPublicId().equals(companyId) && !currentUser.hasAuthority("PROJECT_MODERATE")) {
            // requireCompany already checked membership. ORG_ADMIN has COMPANY_MANAGE. Recruiters do not.
        }
        return toCompany(membership.getCompany(), List.of());
    }

    @Transactional(readOnly = true)
    public CompanyView companyForMember(String companyId) {
        Company company = companies.findByPublicId(companyId).orElseThrow(() -> AppException.notFound("Company not found."));
        boolean member = currentUser.require() != null && company.getStatus() == com.firstrep.common.Kinds.CompanyStatus.ACTIVE;
        access.requireCompany(companyId, "PROJECT_EDIT_OWN_COMPANY");
        return toCompany(company, List.of());
    }

    @Transactional
    public CompanyView updateCompany(String companyId, CompanyWrite write) {
        CompanyMembership membership = access.requireCompany(companyId, "COMPANY_MANAGE");
        Company company = membership.getCompany();
        company.setName(required(write.name(), 200, "Company name"));
        company.setDescription(clip(write.description(), 4000));
        company.setLocation(clip(write.location(), 200));
        company.setWebsite(clip(write.website(), 500));
        audit.record(currentUser.require(), "COMPANY_UPDATED", "COMPANY", company.getPublicId(), company.getId(), "Profile updated");
        return toCompany(company, List.of());
    }

    @Transactional(readOnly = true)
    public List<CompanyView> publicCompanies() {
        return companies.findWithPublishedProjects().stream().map(company -> new CompanyView(company.getPublicId(), company.getName(), company.getLocation(), null, null, company.getStatus().name())).toList();
    }

    @Transactional(readOnly = true)
    public List<SkillView> listSkills() {
        return skills.findAllByOrderByNameAsc().stream().map(skill -> new SkillView(skill.getName(), skill.getSlug())).toList();
    }

    @Transactional
    public ProjectDetail createProject(String companyId, ProjectWrite write) {
        CompanyMembership membership = access.requireCompany(companyId, "PROJECT_CREATE");
        return saveNew(membership.getCompany(), currentUser.require(), write);
    }

    @Transactional
    public ProjectDetail updateProject(String companyId, String projectId, ProjectWrite write) {
        access.requireCompany(companyId, "PROJECT_EDIT_OWN_COMPANY");
        Project project = requireOwned(companyId, projectId);
        if (project.getStatus() == ProjectStatus.ARCHIVED || project.getStatus() == ProjectStatus.CLOSED) {
            throw AppException.bad("PROJECT_CLOSED", "Archived or closed projects cannot be edited.");
        }
        applyBasics(project, write);
        replaceDeliverables(project, write.deliverables());
        replaceSkills(project, write.skillNames());
        replaceRubric(project, write.criteria(), project.getStatus() != ProjectStatus.DRAFT);
        replaceRound(project, write);
        audit.record(currentUser.require(), "PROJECT_UPDATED", "PROJECT", project.getPublicId(), project.getCompany().getId(), project.getStatus().name());
        return toDetail(project, true);
    }

    @Transactional
    public ProjectDetail submitForApproval(String companyId, String projectId) {
        access.requireCompany(companyId, "PROJECT_EDIT_OWN_COMPANY");
        Project project = requireOwned(companyId, projectId);
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw AppException.bad("BAD_STATE", "Only a draft can be submitted for approval.");
        }
        if (deliverables.findByProjectIdOrderBySortOrderAsc(project.getId()).isEmpty() || currentCriteria(project).isEmpty()) {
            throw AppException.bad("INCOMPLETE_PROJECT", "Add deliverables and at least one rubric criterion first.");
        }
        project.setStatus(ProjectStatus.PENDING_MODERATION);
        audit.record(currentUser.require(), "PROJECT_SUBMITTED", "PROJECT", project.getPublicId(), project.getCompany().getId(), "Submitted for moderation");
        return toDetail(project, true);
    }

    @Transactional
    public ProjectDetail moderate(String projectId, String action, String note) {
        access.requirePlatform("PROJECT_MODERATE");
        Project project = projects.findByPublicId(projectId).orElseThrow(() -> AppException.notFound("Project not found."));
        switch (action) {
            case "approve" -> {
                if (project.getStatus() != ProjectStatus.PENDING_MODERATION) {
                    throw AppException.bad("BAD_STATE", "Only a project waiting for moderation can be approved.");
                }
                project.setStatus(ProjectStatus.PUBLISHED);
                project.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
                project.setModerationNote(clip(note, 2000));
                publishRubric(project);
            }
            case "reject" -> {
                if (note == null || note.isBlank()) {
                    throw AppException.bad("NOTE_REQUIRED", "Add a note the company can act on.");
                }
                project.setStatus(ProjectStatus.DRAFT);
                project.setModerationNote(clip(note, 2000));
            }
            case "pause" -> {
                project.setStatus(ProjectStatus.PAUSED);
                project.setModerationNote(clip(note, 2000));
            }
            case "close", "archive" -> {
                if (note == null || note.isBlank()) {
                    throw AppException.bad("NOTE_REQUIRED", "Record why the project is being withdrawn.");
                }
                project.setStatus("archive".equals(action) ? ProjectStatus.ARCHIVED : ProjectStatus.CLOSED);
                project.setArchivedAt(LocalDateTime.now(ZoneOffset.UTC));
                project.setModerationNote(clip(note, 2000));
                enrollmentService.restoreWithdrawn(project);
            }
            default -> throw AppException.bad("BAD_ACTION", "Unknown moderation action.");
        }
        audit.record(currentUser.require(), "PROJECT_" + action.toUpperCase(), "PROJECT", project.getPublicId(), project.getCompany().getId(), clip(note, 200));
        return toDetail(project, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummary> search(String q, String role, String difficulty, String companyId, String skill, Integer effortMax, int page, int size, String sort) {
        Difficulty parsed = difficulty == null || difficulty.isBlank() ? null : parseDifficulty(difficulty);
        Sort springSort = "deadline".equals(sort) ? Sort.by("deadline").ascending() : Sort.by("publishedAt").descending();
        var result = projects.search(blankToNull(q), blankToNull(role), parsed, blankToNull(companyId), blankToNull(skill), effortMax,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), springSort));
        return new PageResponse<>(result.getContent().stream().map(this::toSummary).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> moderationQueue() {
        access.requirePlatform("PROJECT_MODERATE");
        return projects.findByStatusNotOrderByUpdatedAtDesc(ProjectStatus.DRAFT).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetail adminDetail(String projectId) {
        access.requirePlatform("PROJECT_MODERATE");
        return toDetail(projects.findByPublicId(projectId).orElseThrow(() -> AppException.notFound("Project not found.")), true);
    }

    @Transactional(readOnly = true)
    public ProjectDetail publicDetail(String projectId) {
        Project project = projects.findByPublicId(projectId).orElseThrow(() -> AppException.notFound("Project not found."));
        if (project.getStatus() != ProjectStatus.PUBLISHED) {
            throw AppException.notFound("Project not found.");
        }
        return toDetail(project, false);
    }

    @Transactional(readOnly = true)
    public CompanyView viewForMember(String companyId) {
        return toCompany(access.requireMember(companyId).getCompany(), List.of());
    }

    @Transactional(readOnly = true)
    public List<ProjectDetail> companyProjects(String companyId) {
        CompanyMembership membership = access.requireMember(companyId);
        boolean editor = membership.getRoles().stream().anyMatch(role -> role.getName().equals("ORG_ADMIN"));
        return projects.findByCompanyIdOrderByUpdatedAtDesc(membership.getCompany().getId()).stream()
                .map(project -> toDetail(project, editor)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetail companyProject(String companyId, String projectId) {
        access.requireCompany(companyId, "PROJECT_EDIT_OWN_COMPANY");
        return toDetail(requireOwned(companyId, projectId), true);
    }

    public Project requireOwned(String companyId, String projectId) {
        Project project = projects.findByPublicId(projectId).orElseThrow(() -> AppException.notFound("Project not found."));
        if (!project.getCompany().getPublicId().equals(companyId)) {
            throw AppException.notFound("Project not found.");
        }
        return project;
    }

    public Project requirePublished(String projectId) {
        return projects.findByPublicId(projectId).filter(project -> project.getStatus() == ProjectStatus.PUBLISHED)
                .orElseThrow(() -> AppException.notFound("Project not found."));
    }

    private ProjectDetail saveNew(Company company, UserAccount actor, ProjectWrite write) {
        Project project = new Project();
        project.setPublicId(Ids.uuid());
        project.setCompany(company);
        project.setCreatedBy(actor);
        project.setStatus(ProjectStatus.DRAFT);
        applyBasics(project, write);
        projects.save(project);
        replaceSkills(project, write.skillNames());
        replaceDeliverables(project, write.deliverables());
        RubricVersion version = new RubricVersion();
        version.setProject(project);
        version.setVersionNumber(1);
        version.setStatus(RubricStatus.DRAFT);
        version.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        rubrics.save(version);
        writeCriteria(version, write.criteria());
        replaceRound(project, write);
        audit.record(actor, "PROJECT_CREATED", "PROJECT", project.getPublicId(), company.getId(), project.getTitle());
        return toDetail(project, true);
    }

    private void applyBasics(Project project, ProjectWrite write) {
        project.setTitle(required(write.title(), 200, "Title"));
        project.setSummary(required(write.summary(), 600, "Summary"));
        project.setDescription(required(write.description(), 20000, "Description"));
        project.setRoleTag(required(write.roleTag(), 120, "Role"));
        project.setDifficulty(parseDifficulty(write.difficulty()));
        if (write.effortHours() < 1 || write.effortHours() > 400) {
            throw AppException.bad("VALIDATION", "Effort must be between 1 and 400 hours.");
        }
        project.setEffortHours(write.effortHours());
        project.setDeadline(write.deadline());
        project.setEligibility(clip(write.eligibility(), 4000));
        project.setCertificateConditions(clip(write.certificateConditions(), 4000));
        project.setRecruitmentLinked(write.recruitmentLinked());
    }

    private void replaceDeliverables(Project project, List<DeliverableWrite> items) {
        if (items == null || items.isEmpty()) {
            throw AppException.bad("VALIDATION", "Add at least one deliverable.");
        }
        deliverables.deleteByProjectId(project.getId());
        int order = 1;
        for (DeliverableWrite item : items) {
            ProjectDeliverable deliverable = new ProjectDeliverable();
            deliverable.setProject(project);
            deliverable.setTitle(required(item.title(), 200, "Deliverable"));
            deliverable.setDescription(clip(item.description(), 4000));
            deliverable.setRequiredItem(item.required());
            deliverable.setSortOrder(order++);
            deliverables.save(deliverable);
        }
    }

    private void replaceSkills(Project project, List<String> names) {
        project.getSkills().clear();
        if (names == null || names.isEmpty()) {
            throw AppException.bad("VALIDATION", "Tag at least one skill.");
        }
        for (String name : names) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            Skill skill = skills.findByNameIgnoreCase(trimmed).orElseGet(() -> {
                Skill created = new Skill();
                created.setName(trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed);
                created.setSlug(uniqueSkillSlug(Ids.slug(created.getName())));
                return skills.save(created);
            });
            project.getSkills().add(skill);
        }
        if (project.getSkills().isEmpty()) {
            throw AppException.bad("VALIDATION", "Tag at least one skill.");
        }
    }

    private void replaceRubric(Project project, List<CriterionWrite> items, boolean versioned) {
        RubricVersion current = rubrics.findByProjectIdOrderByVersionNumberDesc(project.getId()).stream().findFirst()
                .orElseThrow(() -> AppException.bad("RUBRIC_MISSING", "This project has no rubric."));
        boolean referenced = enrollments.findByProjectId(project.getId()).stream().findAny().isPresent() && versioned;
        if (!versioned || !referenced) {
            criteria.deleteByRubricVersionId(current.getId());
            writeCriteria(current, items);
            return;
        }
        current.setStatus(RubricStatus.SUPERSEDED);
        RubricVersion next = new RubricVersion();
        next.setProject(project);
        next.setVersionNumber(current.getVersionNumber() + 1);
        next.setStatus(project.getStatus() == ProjectStatus.PUBLISHED ? RubricStatus.PUBLISHED : RubricStatus.DRAFT);
        next.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        rubrics.save(next);
        writeCriteria(next, items);
    }

    private void writeCriteria(RubricVersion version, List<CriterionWrite> items) {
        if (items == null || items.isEmpty()) {
            throw AppException.bad("VALIDATION", "Add at least one rubric criterion.");
        }
        int order = 1;
        for (CriterionWrite item : items) {
            if (item.maxScore() < 1 || item.maxScore() > 20) {
                throw AppException.bad("VALIDATION", "Criterion scores must be between 1 and 20.");
            }
            RubricCriterion criterion = new RubricCriterion();
            criterion.setPublicId(Ids.uuid());
            criterion.setRubricVersion(version);
            criterion.setTitle(required(item.title(), 200, "Criterion"));
            criterion.setDescription(required(item.description(), 4000, "Criterion description"));
            criterion.setMaxScore(item.maxScore());
            criterion.setSortOrder(order++);
            criteria.save(criterion);
        }
    }

    private void publishRubric(Project project) {
        rubrics.findByProjectIdOrderByVersionNumberDesc(project.getId()).stream().findFirst().ifPresent(version -> version.setStatus(RubricStatus.PUBLISHED));
    }

    private void replaceRound(Project project, ProjectWrite write) {
        rounds.findByProjectId(project.getId()).ifPresent(existing -> {
            questions.deleteByRoundId(existing.getId());
            rounds.delete(existing);
        });
        if (!write.recruitmentLinked() || write.skillsQuestions() == null || write.skillsQuestions().isEmpty()) {
            return;
        }
        SkillsRound round = new SkillsRound();
        round.setPublicId(Ids.uuid());
        round.setProject(project);
        round.setTitle(project.getTitle() + " skills round");
        round.setInstructions("Fixed questions. Multiple choice is scored automatically. Short answers are kept for a person to read and are not auto-scored. This is not an adaptive exam.");
        int passing = 0;
        rounds.save(round);
        int order = 1;
        for (QuestionWrite item : write.skillsQuestions()) {
            QuestionType type = "SHORT_ANSWER".equals(item.questionType()) ? QuestionType.SHORT_ANSWER : QuestionType.MULTIPLE_CHOICE;
            SkillsQuestion question = new SkillsQuestion();
            question.setPublicId(Ids.uuid());
            question.setRound(round);
            question.setPrompt(required(item.prompt(), 2000, "Question"));
            question.setQuestionType(type);
            question.setSortOrder(order++);
            question.setMaxScore(1);
            if (type == QuestionType.MULTIPLE_CHOICE) {
                if (item.options() == null || item.options().size() < 2) {
                    throw AppException.bad("VALIDATION", "Multiple choice questions need at least two options.");
                }
                try {
                    question.setOptionsJson(objectMapper.writeValueAsString(item.options()));
                } catch (Exception ex) {
                    throw AppException.bad("VALIDATION", "Options could not be saved.");
                }
                question.setCorrectOption(item.correctOption());
                passing++;
            }
            questions.save(question);
        }
        round.setPassingScore(passing);
    }

    private List<RubricCriterion> currentCriteria(Project project) {
        return rubrics.findByProjectIdOrderByVersionNumberDesc(project.getId()).stream().findFirst()
                .map(version -> criteria.findByRubricVersionIdOrderBySortOrderAsc(version.getId()))
                .orElse(List.of());
    }

    public List<RubricCriterion> publishedCriteria(Project project) {
        return rubrics.findFirstByProjectIdAndStatusOrderByVersionNumberDesc(project.getId(), RubricStatus.PUBLISHED)
                .map(version -> criteria.findByRubricVersionIdOrderBySortOrderAsc(version.getId()))
                .orElse(List.of());
    }

    private ProjectSummary toSummary(Project project) {
        return new ProjectSummary(project.getPublicId(), project.getCompany().getPublicId(), project.getCompany().getName(),
                project.getTitle(), project.getSummary(), project.getRoleTag(), project.getDifficulty().name(), project.getEffortHours(),
                project.getDeadline() == null ? null : project.getDeadline().toString(), project.isRecruitmentLinked(), project.getStatus().name(),
                project.getSkills().stream().map(Skill::getName).sorted().toList());
    }

    private ProjectDetail toDetail(Project project, boolean editor) {
        List<DeliverableView> deliverableViews = deliverables.findByProjectIdOrderBySortOrderAsc(project.getId()).stream()
                .map(item -> new DeliverableView(item.getTitle(), item.getDescription(), item.isRequiredItem())).toList();
        List<CriterionView> criterionViews = currentCriteria(project).stream()
                .map(item -> new CriterionView(item.getPublicId(), item.getTitle(), item.getDescription(), item.getMaxScore())).toList();
        List<QuestionView> questionViews = new ArrayList<>();
        rounds.findByProjectId(project.getId()).ifPresent(round -> questions.findByRoundIdOrderBySortOrderAsc(round.getId()).forEach(question -> {
            List<String> options = readOptions(question.getOptionsJson());
            questionViews.add(new QuestionView(question.getPublicId(), question.getPrompt(), question.getQuestionType().name(), options,
                    editor ? question.getCorrectOption() : null));
        }));
        String enrollmentId = null;
        try {
            var user = currentUser.principal();
            enrollmentId = enrollments.findByProjectIdAndStudentId(project.getId(), user.id()).map(item -> item.getPublicId()).orElse(null);
        } catch (AppException ignored) {
            enrollmentId = null;
        }
        return new ProjectDetail(toSummary(project), project.getDescription(), project.getEligibility(), project.getCertificateConditions(),
                project.getModerationNote(), deliverableViews, criterionViews, questionViews, enrollmentId);
    }

    private List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private CompanyView toCompany(Company company, List<String> ignored) {
        return new CompanyView(company.getPublicId(), company.getName(), company.getLocation(), company.getDescription(), company.getWebsite(), company.getStatus().name());
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int i = 2;
        while (companies.findBySlug(slug).isPresent()) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    private String uniqueSkillSlug(String base) {
        String slug = base;
        int i = 2;
        while (true) {
            String candidate = slug;
            if (skills.findAll().stream().noneMatch(skill -> skill.getSlug().equals(candidate))) {
                break;
            }
            slug = base + "-" + i++;
        }
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    private static Difficulty parseDifficulty(String value) {
        try {
            return Difficulty.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw AppException.bad("VALIDATION", "Difficulty must be INTRODUCTORY, INTERMEDIATE, or ADVANCED.");
        }
    }

    private static String required(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            throw AppException.bad("VALIDATION", label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw AppException.bad("VALIDATION", label + " is too long.");
        }
        return trimmed;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CompanyWrite(@NotBlank @Size(max = 200) String name, @Size(max = 4000) String description,
                               @Size(max = 200) String location, @Size(max = 500) String website) {}
    public record CompanyView(String id, String name, String location, String description, String website, String status) {}
    public record SkillView(String name, String slug) {}
    public record DeliverableWrite(@NotBlank String title, String description, boolean required) {}
    public record CriterionWrite(@NotBlank String title, @NotBlank String description, @Min(1) @Max(20) int maxScore) {}
    public record QuestionWrite(@NotBlank String prompt, String questionType, List<String> options, String correctOption) {}
    public record ProjectWrite(@NotBlank String title, @NotBlank String summary, @NotBlank String description, @NotBlank String roleTag,
                               @NotBlank String difficulty, int effortHours, LocalDateTime deadline, String eligibility,
                               String certificateConditions, boolean recruitmentLinked, @NotEmpty List<String> skillNames,
                               @NotEmpty @Valid List<DeliverableWrite> deliverables, @NotEmpty @Valid List<CriterionWrite> criteria,
                               List<QuestionWrite> skillsQuestions) {}
    public record ProjectSummary(String id, String companyId, String companyName, String title, String summary, String roleTag,
                                 String difficulty, int effortHours, String deadline, boolean recruitmentLinked, String status, List<String> skills) {}
    public record DeliverableView(String title, String description, boolean required) {}
    public record CriterionView(String id, String title, String description, int maxScore) {}
    public record QuestionView(String id, String prompt, String questionType, List<String> options, String correctOption) {}
    public record ProjectDetail(ProjectSummary summary, String description, String eligibility, String certificateConditions,
                                String moderationNote, List<DeliverableView> deliverables, List<CriterionView> rubric,
                                List<QuestionView> skillsQuestions, String enrollmentId) {}
    public record ModerationRequest(String note) {}
}
