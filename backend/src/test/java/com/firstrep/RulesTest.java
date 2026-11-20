package com.firstrep;

import com.firstrep.assessment.AssessmentModels.AssessmentDraft;
import com.firstrep.assessment.AssessmentModels.AssessmentRequest;
import com.firstrep.assessment.AssessmentModels.CriterionDraft;
import com.firstrep.assessment.AssessmentModels.CriterionView;
import com.firstrep.assessment.AssessmentValidator;
import com.firstrep.assessment.MockAssessmentProvider;
import com.firstrep.common.AppException;
import com.firstrep.common.UploadInspector;
import com.firstrep.common.UrlSafety;
import com.firstrep.config.AppProperties;
import com.firstrep.entitlement.StripeSignatures;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    @Test
    void mockAssessmentIsDeterministicAndInsideTheRubric() {
        AppProperties properties = new AppProperties();
        MockAssessmentProvider provider = new MockAssessmentProvider(properties);
        var criteria = List.of(new CriterionView("c1", "Handover record", "Stores a note", 5));
        AssessmentRequest request = new AssessmentRequest("Shift handover", "v1", criteria, "handover note in the readme");
        AssessmentDraft first = provider.assess(request);
        AssessmentDraft second = provider.assess(request);
        AssessmentValidator.validate(first, criteria);
        assertEquals(first.criteria().get(0).score(), second.criteria().get(0).score());
        assertTrue(first.summary().toLowerCase().contains("not a hiring"));
        assertEquals(5, first.criteria().get(0).maxScore());
    }

    @Test
    void validatorRejectsAScoreOutsideTheRubric() {
        var criteria = List.of(new CriterionView("c1", "Runs", "It runs", 5));
        AssessmentDraft draft = new AssessmentDraft("Summary", List.of(new CriterionDraft(
                "c1", 9, 5, "evidence", "strength", "gap", "feedback", "HIGH", List.of())));
        assertThrows(AppException.class, () -> AssessmentValidator.validate(draft, criteria));
    }

    @Test
    void urlsBlockPrivateTargetsAndAcceptPublicGithub() {
        assertEquals("GITHUB", UrlSafety.check("https://github.com/north-harbour/handover").kind().name());
        assertThrows(AppException.class, () -> UrlSafety.check("http://github.com/north-harbour/handover"));
        assertThrows(AppException.class, () -> UrlSafety.check("https://localhost/secret"));
        assertThrows(AppException.class, () -> UrlSafety.check("https://127.0.0.1/secret"));
        assertThrows(AppException.class, () -> UrlSafety.check("https://169.254.169.254/latest/meta-data"));
    }

    @Test
    void stripeSignatureRejectsTamperingAndAcceptsAFreshSignature() {
        String payload = "{\"id\":\"evt_1\"}";
        String secret = "whsec_test";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = StripeSignatures.hmac(secret, timestamp + "." + payload);
        StripeSignatures.verify(payload, "t=" + timestamp + ",v1=" + signature, secret, Duration.ofMinutes(5));
        assertThrows(AppException.class, () -> StripeSignatures.verify(payload, "t=" + timestamp + ",v1=deadbeef", secret, Duration.ofMinutes(5)));
        assertThrows(AppException.class, () -> StripeSignatures.verify(payload, "t=" + timestamp + ",v1=" + signature, "", Duration.ofMinutes(5)));
    }

    @Test
    void uploadsRejectExecutablesAndAcceptText() {
        assertThrows(AppException.class, () -> UploadInspector.inspect("run.exe", "hello".getBytes(StandardCharsets.UTF_8)));
        var text = UploadInspector.inspect("notes.md", "# handover\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("text/markdown", text.contentType());
        assertThrows(AppException.class, () -> UploadInspector.inspect("photo.png", "not a png".getBytes(StandardCharsets.UTF_8)));
    }
}
