package com.firstrep.admin;

import com.firstrep.access.RoleRepository;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.CompanyStatus;
import com.firstrep.common.Kinds.MembershipStatus;
import com.firstrep.company.Company;
import com.firstrep.company.CompanyMembership;
import com.firstrep.company.CompanyMembershipRepository;
import com.firstrep.company.CompanyRepository;
import com.firstrep.config.SecurityConfig.AuthPrincipal;
import com.firstrep.identity.IdentityService;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import com.firstrep.project.CatalogService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@ConditionalOnProperty(name = "firstrep.seed.enabled", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {
    public static final String PASSWORD = "Demo-FirstRep-2026";
    private final UserAccountRepository users;
    private final IdentityService identity;
    private final CompanyRepository companies;
    private final CompanyMembershipRepository memberships;
    private final RoleRepository roles;
    private final CatalogService catalog;

    public DemoSeeder(UserAccountRepository users, IdentityService identity, CompanyRepository companies,
                      CompanyMembershipRepository memberships, RoleRepository roles, CatalogService catalog) {
        this.users = users;
        this.identity = identity;
        this.companies = companies;
        this.memberships = memberships;
        this.roles = roles;
        this.catalog = catalog;
    }

    @Override
    public void run(String... args) {
        if (users.findByEmailIgnoreCase("student@firstrep.local").isPresent()) {
            return;
        }
        identity.createActiveUser("student@firstrep.local", PASSWORD, "Demo Student", "STUDENT", true);
        UserAccount org = identity.createActiveUser("orgadmin@firstrep.local", PASSWORD, "Demo Org Admin", null, false);
        UserAccount recruiter = identity.createActiveUser("recruiter@firstrep.local", PASSWORD, "Demo Recruiter", null, false);
        UserAccount reviewer = identity.createActiveUser("reviewer@firstrep.local", PASSWORD, "Demo Reviewer", null, false);
        UserAccount admin = identity.createActiveUser("admin@firstrep.local", PASSWORD, "Demo Platform Admin", "PLATFORM_ADMIN", false);
        UserAccount lake = identity.createActiveUser("lakeadmin@firstrep.local", PASSWORD, "Demo Lake Admin", null, false);

        Company harbour = company("North Harbour Systems", "Vaasa", "A small Vaasa team publishing practical projects for early-career candidates.");
        Company lakeworks = company("Lakeworks Studio", "Vaasa", "A second pilot company used to check that evidence stays inside one organisation.");
        member(harbour, org, "ORG_ADMIN");
        member(harbour, recruiter, "RECRUITER");
        member(harbour, reviewer, "REVIEWER");
        member(lakeworks, lake, "ORG_ADMIN");

        as(org);
        var handover = catalog.createProject(harbour.getPublicId(), project(
                "Shift handover API",
                "A small HTTP API that records a shift handover between two operators.",
                "Build a readable API for a harbour operations desk. Store a handover note, who wrote it, and the time. Keep the design small and tested.",
                "Software development", "INTERMEDIATE", 12, false,
                List.of("Java", "REST APIs", "Testing"),
                List.of(new CatalogService.DeliverableWrite("README", "How to run the API", true),
                        new CatalogService.DeliverableWrite("API description", "Routes, status codes, and one example request", true)),
                List.of(new CatalogService.CriterionWrite("Runs locally", "A reviewer can start the project from the README.", 5),
                        new CatalogService.CriterionWrite("Handover record", "The API stores author, time, and note.", 5),
                        new CatalogService.CriterionWrite("Tests or checks", "There is a repeatable check for the main path.", 5)),
                List.of()));
        catalog.submitForApproval(harbour.getPublicId(), handover.summary().id());

        var notebook = catalog.createProject(harbour.getPublicId(), project(
                "Berth occupancy notes",
                "Tidy a small occupancy table and explain what a planner should notice.",
                "You receive a short table of berth occupancy. Clean the obvious issues and write what changed. A chart is welcome, not required.",
                "Data analytics", "INTRODUCTORY", 8, false,
                List.of("SQL", "Python", "Data visualisation"),
                List.of(new CatalogService.DeliverableWrite("Cleaned table", "CSV or a notebook output", true),
                        new CatalogService.DeliverableWrite("Short note", "What you changed and why", true)),
                List.of(new CatalogService.CriterionWrite("Data is usable", "The table can be read without guessing the columns.", 5),
                        new CatalogService.CriterionWrite("Changes are explained", "The note says what was wrong and what you did.", 5)),
                List.of()));
        catalog.submitForApproval(harbour.getPublicId(), notebook.summary().id());

        var triage = catalog.createProject(harbour.getPublicId(), project(
                "Support note triage",
                "Sort a handful of support notes and suggest a next step for each.",
                "This recruitment-linked project asks for a short written triage and a fixed skills round. A person still decides whether to talk to you.",
                "AI and support operations", "INTERMEDIATE", 10, true,
                List.of("Python", "Communication"),
                List.of(new CatalogService.DeliverableWrite("Triage table", "Each note, a category, and a next step", true)),
                List.of(new CatalogService.CriterionWrite("Categories are consistent", "Similar notes are grouped in a way you can explain.", 5),
                        new CatalogService.CriterionWrite("Next steps are concrete", "A teammate could act on the suggestion.", 5)),
                List.of(new CatalogService.QuestionWrite("Which status means the customer is waiting on us?", "MULTIPLE_CHOICE",
                                List.of("Resolved", "Waiting on us", "Waiting on the customer"), "B"),
                        new CatalogService.QuestionWrite("A note says the crane stopped at 02:10. What should you record first?", "MULTIPLE_CHOICE",
                                List.of("A guess about the cause", "The time and the reported symptom", "A hiring recommendation"), "B"),
                        new CatalogService.QuestionWrite("In a few sentences, how would you tell a teammate the note is incomplete?", "SHORT_ANSWER", List.of(), null))));
        catalog.submitForApproval(harbour.getPublicId(), triage.summary().id());

        catalog.createProject(harbour.getPublicId(), project(
                "Tide table cleaner",
                "A draft waiting for platform approval.",
                "Clean a week of tide readings and flag gaps. This stays in moderation until an admin approves it.",
                "Data analytics", "INTRODUCTORY", 6, false,
                List.of("SQL"),
                List.of(new CatalogService.DeliverableWrite("Gap list", "Times with missing readings", true)),
                List.of(new CatalogService.CriterionWrite("Gaps are listed", "Missing times are visible.", 5)),
                List.of()));
        var pending = catalog.companyProjects(harbour.getPublicId()).stream()
                .filter(item -> "Tide table cleaner".equals(item.summary().title())).findFirst().orElseThrow();
        catalog.submitForApproval(harbour.getPublicId(), pending.summary().id());

        as(admin);
        for (var detail : List.of(handover, notebook, triage)) {
            catalog.moderate(detail.summary().id(), "approve", "Suitable for the Vaasa pilot.");
        }

        as(lake);
        var inventory = catalog.createProject(lakeworks.getPublicId(), project(
                "Inventory count sheet",
                "A published project from another company.",
                "Turn a paper count into a small table. Used so a second company is visible in the catalogue.",
                "Operations", "INTRODUCTORY", 5, false,
                List.of("SQL"),
                List.of(new CatalogService.DeliverableWrite("Count table", "Item, quantity, note", true)),
                List.of(new CatalogService.CriterionWrite("Quantities are numeric", "Quantities are not stored as prose.", 5)),
                List.of()));
        catalog.submitForApproval(lakeworks.getPublicId(), inventory.summary().id());
        as(admin);
        catalog.moderate(inventory.summary().id(), "approve", "Published for catalogue isolation checks.");
        SecurityContextHolder.clearContext();
    }

    private CatalogService.ProjectWrite project(String title, String summary, String description, String role, String difficulty,
                                                int hours, boolean recruitment, List<String> skills, List<CatalogService.DeliverableWrite> deliverables,
                                                List<CatalogService.CriterionWrite> criteria, List<CatalogService.QuestionWrite> questions) {
        return new CatalogService.ProjectWrite(title, summary, description, role, difficulty, hours,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(60), "Open to students and recent graduates in the Vaasa pilot.",
                "A certificate is issued only after a human reviewer approves the project assessment.",
                recruitment, skills, deliverables, criteria, questions);
    }

    private Company company(String name, String location, String description) {
        Company company = new Company();
        company.setPublicId(Ids.uuid());
        company.setName(name);
        company.setSlug(Ids.slug(name));
        company.setLocation(location);
        company.setDescription(description);
        company.setStatus(CompanyStatus.ACTIVE);
        return companies.save(company);
    }

    private void member(Company company, UserAccount user, String role) {
        CompanyMembership membership = new CompanyMembership();
        membership.setCompany(company);
        membership.setUser(user);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.getRoles().add(roles.findByName(role).orElseThrow());
        memberships.save(membership);
    }

    private void as(UserAccount user) {
        var authorities = users.platformPermissions(user.getId()).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getPublicId(), user.getEmail()), null, authorities));
    }
}
