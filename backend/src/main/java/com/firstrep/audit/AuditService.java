package com.firstrep.audit;

import com.firstrep.identity.UserAccount;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional
    public void record(UserAccount actor, String action, String resourceType, String resourceId, Long companyId, String detail) {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setCompanyId(companyId);
        event.setDetail(clip(detail));
        event.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        events.save(event);
    }

    private static String clip(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 500 ? detail : detail.substring(0, 500);
    }
}
