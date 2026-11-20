package com.firstrep.assessment;

import com.firstrep.assessment.AssessmentModels.AssessmentDraft;
import com.firstrep.assessment.AssessmentModels.AssessmentRequest;
import com.firstrep.assessment.AssessmentModels.CriterionDraft;
import com.firstrep.assessment.AssessmentModels.CriterionView;
import com.firstrep.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MockAssessmentProvider implements AssessmentProvider {
    private final AppProperties properties;

    public MockAssessmentProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        String configured = properties.getAi().getModel();
        return configured == null || configured.isBlank() ? "deterministic-mock" : configured;
    }

    @Override
    public AssessmentDraft assess(AssessmentRequest request) {
        String evidence = request.untrustedEvidence() == null ? "" : request.untrustedEvidence();
        String lowered = evidence.toLowerCase(Locale.ROOT);
        boolean thin = lowered.isBlank();
        List<CriterionDraft> results = new ArrayList<>();
        for (CriterionView criterion : request.criteria()) {
            int hits = 0;
            for (String token : criterion.title().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.length() > 3 && lowered.contains(token)) {
                    hits++;
                }
            }
            double ratio = thin ? 0.35 : Math.min(0.95, 0.55 + hits * 0.10);
            int score = (int) Math.round(ratio * criterion.maxScore());
            score = Math.max(0, Math.min(criterion.maxScore(), score));
            String confidence = thin ? "LOW" : hits == 0 ? "MEDIUM" : "HIGH";
            List<String> flags = thin ? List.of("thin_evidence") : List.of();
            String reference = thin ? "No file, link, or note was available to cite." : clip(evidence.replace('\n', ' '), 280);
            results.add(new CriterionDraft(
                    criterion.id(),
                    score,
                    criterion.maxScore(),
                    reference,
                    "The draft notes what is present against \"" + criterion.title() + "\".",
                    score == criterion.maxScore() ? "No obvious gap against this criterion in the supplied evidence."
                            : "The evidence does not yet fully cover \"" + criterion.title() + "\".",
                    "Human reviewer: compare the evidence with the published criterion \"" + criterion.title() + "\" before releasing feedback.",
                    confidence,
                    flags));
        }
        String summary = "Deterministic draft for " + request.projectTitle()
                + ". This is not a hiring decision and not final feedback. A reviewer must approve, request changes, or reject it.";
        return new AssessmentDraft(summary, results);
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
