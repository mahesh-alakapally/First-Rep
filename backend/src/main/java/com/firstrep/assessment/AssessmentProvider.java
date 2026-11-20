package com.firstrep.assessment;

import com.firstrep.assessment.AssessmentModels.AssessmentDraft;
import com.firstrep.assessment.AssessmentModels.AssessmentRequest;

public interface AssessmentProvider {
    String name();

    String model();

    AssessmentDraft assess(AssessmentRequest request);
}
