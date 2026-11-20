package com.firstrep.worker;

import com.firstrep.assessment.AssessmentMessage;
import com.firstrep.assessment.AssessmentProcessor;
import com.firstrep.config.AppProperties;
import com.firstrep.config.MessagingConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "firstrep.worker.consume", havingValue = "true")
public class AssessmentListener {
    private final AssessmentProcessor processor;
    private final RabbitTemplate rabbitTemplate;
    private final AppProperties properties;

    public AssessmentListener(AssessmentProcessor processor, RabbitTemplate rabbitTemplate, AppProperties properties) {
        this.processor = processor;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @RabbitListener(queues = MessagingConfig.ASSESSMENT_QUEUE)
    public void onMessage(AssessmentMessage message) {
        try {
            processor.process(message.submissionPublicId());
        } catch (Exception ex) {
            int next = message.attempt() + 1;
            if (next >= properties.getWorker().getMaxAttempts()) {
                processor.markManual(message.submissionPublicId(), ex.getMessage());
                return;
            }
            processor.markRetrying(message.submissionPublicId(), ex.getMessage());
            rabbitTemplate.convertAndSend(MessagingConfig.ASSESSMENT_QUEUE, new AssessmentMessage(message.submissionPublicId(), next));
        }
    }
}
