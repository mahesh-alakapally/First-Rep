package com.firstrep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstrep.identity.IdentityService;
import com.firstrep.identity.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class JourneyIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = MYSQL.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "tinyInt1isBit=false";
        });
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("firstrep.outbox.publish", () -> "true");
        registry.add("firstrep.worker.consume", () -> "true");
        registry.add("firstrep.storage.mode", () -> "memory");
        registry.add("firstrep.seed.enabled", () -> "false");
        registry.add("firstrep.dev.expose-email-tokens", () -> "true");
        registry.add("firstrep.billing.mode", () -> "simulator");
        registry.add("firstrep.ai.provider", () -> "mock");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired IdentityService identity;
    @Autowired UserAccountRepository users;

    @BeforeEach
    void admin() {
        if (users.findByEmailIgnoreCase("admin@test.local").isEmpty()) {
            identity.createActiveUser("admin@test.local", "Demo-FirstRep-2026", "Test Admin", "PLATFORM_ADMIN", false);
        }
    }

    @Test
    void pilotJourneyRejectsIsolationLeaksAndNeedsPaymentBeforeAThirdProject() throws Exception {
        JsonNode plans = json.readTree(mvc.perform(get("/api/v1/billing/plans")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(plans.toString().contains("1900"));
        assertTrue(plans.toString().contains("19900"));

        Session founder = registerAndLogin("founder@test.local", "Harbour Founder");
        JsonNode company = json.readTree(mvc.perform(post("/api/v1/companies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Harbour\",\"location\":\"Vaasa\",\"description\":\"Pilot company\"}")
                .with(founder.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String companyId = company.get("id").asText();
        String projectBody = """
                {"title":"%s","summary":"A practical brief","description":"Do the work and explain it.","roleTag":"Software development","difficulty":"INTERMEDIATE","effortHours":8,"eligibility":"Pilot students","certificateConditions":"Human approval","recruitmentLinked":%s,"skillNames":["Java"],"deliverables":[{"title":"Note","description":"A short note","required":true}],"criteria":[{"title":"Handover record","description":"Show the work","maxScore":5}],"skillsQuestions":%s}
                """;
        List<String> projectIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            boolean recruitment = i == 3;
            String questions = recruitment
                    ? "[{\"prompt\":\"Which comes first?\",\"questionType\":\"MULTIPLE_CHOICE\",\"options\":[\"Guess\",\"Symptom\"],\"correctOption\":\"B\"}]"
                    : "[]";
            JsonNode created = json.readTree(mvc.perform(post("/api/v1/companies/" + companyId + "/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(projectBody.formatted("Project " + i, recruitment, questions))
                    .with(founder.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String projectId = created.get("summary").get("id").asText();
            mvc.perform(post("/api/v1/companies/" + companyId + "/projects/" + projectId + "/submit-for-approval").with(founder.auth()))
                    .andExpect(status().isOk());
            projectIds.add(projectId);
        }
        Session admin = login("admin@test.local", "Demo-FirstRep-2026");
        for (String projectId : projectIds) {
            mvc.perform(post("/api/v1/admin/projects/" + projectId + "/approve").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"note\":\"Approved for the test\"}").with(admin.auth())).andExpect(status().isOk());
        }

        Session student = registerAndLogin("student@test.local", "Test Student");
        Session other = registerAndLogin("other@test.local", "Other Student");
        mvc.perform(post("/api/v1/projects/" + projectIds.get(0) + "/enroll").with(student.auth())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/" + projectIds.get(1) + "/enroll").with(student.auth())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/" + projectIds.get(2) + "/enroll").with(student.auth())).andExpect(status().isPaymentRequired());

        JsonNode checkout = json.readTree(mvc.perform(post("/api/v1/billing/checkout-session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PREMIUM_MONTHLY\"}").with(student.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("simulator", checkout.get("mode").asText());
        mvc.perform(post("/api/v1/billing/simulator/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkoutId\":\"" + checkout.get("checkoutId").asText() + "\"}").with(student.auth())).andExpect(status().isOk());
        JsonNode third = json.readTree(mvc.perform(post("/api/v1/projects/" + projectIds.get(2) + "/enroll").with(student.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode draft = json.readTree(mvc.perform(post("/api/v1/enrollments/" + third.get("id").asText() + "/submissions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"handover record for the rubric\"}")
                .with(student.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/submissions/" + draft.get("id").asText() + "/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://github.com/north-harbour/handover\"}").with(student.auth())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/submissions/" + draft.get("id").asText() + "/submit").with(student.auth())).andExpect(status().isOk());

        JsonNode ready = null;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            ready = json.readTree(mvc.perform(get("/api/v1/submissions/" + draft.get("id").asText()).with(founder.auth()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            JsonNode assessment = ready.get("assessment");
            if (assessment != null && !assessment.isNull() && "AI_DRAFT_READY".equals(assessment.path("status").asText())) {
                break;
            }
        }
        assertEquals("AI_DRAFT_READY", ready.path("assessment").path("status").asText(), ready.toString());
        JsonNode hidden = json.readTree(mvc.perform(get("/api/v1/submissions/" + draft.get("id").asText()).with(student.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(hidden.get("assessment").get("criteria").isEmpty());

        mvc.perform(get("/api/v1/submissions/" + draft.get("id").asText()).with(other.auth())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/submissions/" + draft.get("id").asText() + "/human-review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"notes\":\"The note matches the rubric.\"}").with(founder.auth())).andExpect(status().isOk());
        JsonNode released = json.readTree(mvc.perform(get("/api/v1/submissions/" + draft.get("id").asText()).with(student.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("RELEASED", released.get("assessment").get("status").asText());
        assertFalse(released.get("assessment").get("criteria").isEmpty());

        JsonNode certs = json.readTree(mvc.perform(get("/api/v1/me/certificates").with(student.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, certs.size());
        String code = certs.get(0).get("verificationCode").asText();
        String verify = mvc.perform(get("/api/v1/certificates/" + code + "/verify")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(verify.contains("student@test.local"));
        assertTrue(verify.contains("not a job offer"));

        JsonNode round = json.readTree(mvc.perform(get("/api/v1/projects/" + projectIds.get(2) + "/skills-round").with(student.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode attempt = json.readTree(mvc.perform(post("/api/v1/skills-rounds/" + round.get("id").asText() + "/attempts").with(student.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/skills-round-attempts/" + attempt.get("id").asText() + "/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"" + round.get("questions").get(0).get("id").asText() + "\",\"answer\":\"B\"}]}")
                .with(student.auth())).andExpect(status().isOk());

        JsonNode share = json.readTree(mvc.perform(post("/api/v1/submissions/" + draft.get("id").asText() + "/share-with-company")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"Vaasa pilot introduction\",\"expiresInDays\":30,\"includeProfile\":true,\"includeCv\":false,\"includeSubmission\":true,\"includeAssessment\":true,\"includeSkills\":true,\"includeCertificate\":true}")
                .with(student.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/companies/" + companyId + "/shares/" + share.get("id").asText()).with(founder.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/companies/" + companyId + "/shares/" + share.get("id").asText()).with(other.auth())).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/shares/" + share.get("id").asText()).with(student.auth()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/companies/" + companyId + "/shares/" + share.get("id").asText()).with(founder.auth())).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"evt_x\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentFreeEnrollmentsCannotExceedTwo() throws Exception {
        Session founder = registerAndLogin("slots-founder@test.local", "Slots Founder");
        JsonNode company = json.readTree(mvc.perform(post("/api/v1/companies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Slot Harbour\",\"location\":\"Vaasa\",\"description\":\"Slots\"}")
                .with(founder.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JsonNode created = json.readTree(mvc.perform(post("/api/v1/companies/" + company.get("id").asText() + "/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(("{\"title\":\"Slot %d\",\"summary\":\"s\",\"description\":\"d\",\"roleTag\":\"Data\",\"difficulty\":\"INTRODUCTORY\",\"effortHours\":4,\"recruitmentLinked\":false,\"skillNames\":[\"SQL\"],\"deliverables\":[{\"title\":\"T\",\"description\":\"D\",\"required\":true}],\"criteria\":[{\"title\":\"Clear\",\"description\":\"Readable\",\"maxScore\":5}]}")
                            .formatted(i))
                    .with(founder.auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String id = created.get("summary").get("id").asText();
            mvc.perform(post("/api/v1/companies/" + company.get("id").asText() + "/projects/" + id + "/submit-for-approval").with(founder.auth())).andExpect(status().isOk());
            ids.add(id);
        }
        Session admin = login("admin@test.local", "Demo-FirstRep-2026");
        for (String id : ids) {
            mvc.perform(post("/api/v1/admin/projects/" + id + "/approve").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}").with(admin.auth())).andExpect(status().isOk());
        }
        Session student = registerAndLogin("slots-student@test.local", "Slots Student");
        var enrollmentService = this.identity; // keep the context warm
        assertTrue(enrollmentService != null);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Callable<Integer>> calls = ids.stream().map(id -> (Callable<Integer>) () -> mvc.perform(post("/api/v1/projects/" + id + "/enroll").with(student.auth())).andReturn().getResponse().getStatus()).toList();
        List<Future<Integer>> futures = pool.invokeAll(calls);
        pool.shutdown();
        int ok = 0;
        int payment = 0;
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 200) {
                ok++;
            } else if (status == 402) {
                payment++;
            }
        }
        assertEquals(2, ok);
        assertEquals(1, payment);
    }

    private Session registerAndLogin(String email, String name) throws Exception {
        Session csrf = csrf();
        MvcResult registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Demo-FirstRep-2026\",\"displayName\":\"" + name + "\"}")
                .with(csrf.auth()))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(registered.getResponse().getContentAsString()).get("devVerificationUrl").asText();
        token = token.substring(token.indexOf("token=") + 6);
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}").with(csrf.auth())).andExpect(status().isOk());
        return login(email, "Demo-FirstRep-2026");
    }

    private Session login(String email, String password) throws Exception {
        Session csrf = csrf();
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}").with(csrf.auth()))
                .andExpect(status().isOk()).andReturn();
        return Session.from(result, csrf);
    }

    private Session csrf() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        return Session.from(result, null);
    }

    private record Session(jakarta.servlet.http.Cookie[] cookies) {
        RequestPostProcessor auth() {
            return request -> {
                request.setCookies(cookies);
                for (var cookie : cookies) {
                    if ("fr_csrf".equals(cookie.getName())) {
                        request.addHeader("X-CSRF-Token", cookie.getValue());
                    }
                }
                return request;
            };
        }

        static Session from(MvcResult result, Session previous) {
            var cookies = new java.util.LinkedHashMap<String, jakarta.servlet.http.Cookie>();
            if (previous != null) {
                for (var cookie : previous.cookies) {
                    cookies.put(cookie.getName(), cookie);
                }
            }
            for (String header : result.getResponse().getHeaders("Set-Cookie")) {
                String[] parts = header.split(";", 2)[0].split("=", 2);
                cookies.put(parts[0], new jakarta.servlet.http.Cookie(parts[0], parts[1]));
            }
            return new Session(cookies.values().toArray(jakarta.servlet.http.Cookie[]::new));
        }
    }
}
