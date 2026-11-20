package com.firstrep.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstrep.assessment.AssessmentMessage;
import com.firstrep.assessment.OutboxEventRepository;
import com.firstrep.common.Kinds.OutboxStatus;
import com.firstrep.config.MessagingConfig;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "firstrep.outbox.publish", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outbox, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publish() {
        for (var event : outbox.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)) {
            try {
                if ("assessment.requested".equals(event.getEventType())) {
                    JsonNode node = objectMapper.readTree(event.getPayload());
                    rabbitTemplate.convertAndSend(MessagingConfig.ASSESSMENT_QUEUE,
                            new AssessmentMessage(node.path("submissionPublicId").asText(), 0));
                }
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
            } catch (Exception ex) {
                log.warn("Outbox event {} stayed pending", event.getId());
                break;
            }
        }
    }
}
