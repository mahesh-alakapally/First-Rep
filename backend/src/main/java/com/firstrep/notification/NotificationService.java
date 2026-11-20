package com.firstrep.notification;

import com.firstrep.common.Ids;
import com.firstrep.config.AppProperties;
import com.firstrep.identity.UserAccount;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public NotificationService(NotificationRepository notifications, JavaMailSender mailSender, AppProperties properties) {
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Transactional
    public void notify(UserAccount user, String title, String body) {
        Notification notification = new Notification();
        notification.setPublicId(Ids.uuid());
        notification.setUser(user);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notifications.save(notification);
        sendMail(user.getEmail(), title, body, false);
    }

    public void sendMail(String to, String subject, String body, boolean required) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getMail().getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException ex) {
            log.warn("Email could not be sent for subject {}", subject);
            if (required && !properties.getDev().isExposeEmailTokens()) {
                throw ex;
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(Long userId) {
        return notifications.findTop30ByUserIdOrderByCreatedAtDesc(userId);
    }
}
