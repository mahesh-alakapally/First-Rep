package com.firstrep.assessment;

import java.util.List;

public final class AssessmentModels {
    private AssessmentModels() {}

    public record CriterionView(String id, String title, String description, int maxScore) {}

    public record AssessmentRequest(String projectTitle, String rubricLabel, List<CriterionView> criteria, String untrustedEvidence) {}

    public record CriterionDraft(
            String criterionId,
            int score,
            int maxScore,
            String evidence,
            String strengths,
            String gaps,
            String feedback,
            String confidence,
            List<String> safetyFlags) {}

    public record AssessmentDraft(String summary, List<CriterionDraft> criteria) {}
}
