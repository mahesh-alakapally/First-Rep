package com.firstrep.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstrep.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Bean
    @ConditionalOnProperty(name = "firstrep.ai.provider", havingValue = "mock", matchIfMissing = true)
    AssessmentProvider mockAssessmentProvider(AppProperties properties) {
        return new MockAssessmentProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "firstrep.ai.provider", havingValue = "openai")
    AssessmentProvider openAiAssessmentProvider(AppProperties properties, ObjectMapper objectMapper) {
        return new OpenAiAssessmentProvider(properties, objectMapper);
    }
}
