package com.firstrep.assessment;

import com.firstrep.assessment.AssessmentModels.AssessmentDraft;
import com.firstrep.assessment.AssessmentModels.CriterionDraft;
import com.firstrep.assessment.AssessmentModels.CriterionView;
import com.firstrep.common.AppException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AssessmentValidator {
    private AssessmentValidator() {}

    public static void validate(AssessmentDraft draft, List<CriterionView> criteria) {
        if (draft == null || draft.summary() == null || draft.summary().isBlank() || draft.summary().length() > 4000) {
            throw AppException.bad("AI_OUTPUT", "Assessment summary was missing or too long.");
        }
        if (draft.criteria() == null || draft.criteria().size() != criteria.size()) {
            throw AppException.bad("AI_OUTPUT", "Assessment did not return one result per rubric criterion.");
        }
        Set<String> expected = new HashSet<>();
        for (CriterionView criterion : criteria) {
            expected.add(criterion.id());
        }
        Set<String> seen = new HashSet<>();
        for (CriterionDraft result : draft.criteria()) {
            if (result.criterionId() == null || !expected.contains(result.criterionId()) || !seen.add(result.criterionId())) {
                throw AppException.bad("AI_OUTPUT", "Assessment referenced an unknown or repeated criterion.");
            }
            CriterionView match = criteria.stream().filter(c -> c.id().equals(result.criterionId())).findFirst().orElseThrow();
            if (result.maxScore() != match.maxScore() || result.score() < 0 || result.score() > match.maxScore()) {
                throw AppException.bad("AI_OUTPUT", "A criterion score was outside the published rubric.");
            }
            requireText(result.evidence(), "evidence");
            requireText(result.strengths(), "strengths");
            requireText(result.gaps(), "gaps");
            requireText(result.feedback(), "feedback");
            if (!Set.of("LOW", "MEDIUM", "HIGH").contains(result.confidence())) {
                throw AppException.bad("AI_OUTPUT", "Assessment confidence was not a known value.");
            }
            if (result.safetyFlags() != null && result.safetyFlags().size() > 8) {
                throw AppException.bad("AI_OUTPUT", "Too many safety flags.");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 4000) {
            throw AppException.bad("AI_OUTPUT", "Assessment " + field + " was missing or too long.");
        }
    }
}
