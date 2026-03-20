package com.finance.tracker.service;

import com.finance.tracker.entity.AuditEvent;
import com.finance.tracker.entity.User;
import com.finance.tracker.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    public void recordEvent(User user, String eventType, Map<String, Object> payload) {
        try {
            AuditEvent event = new AuditEvent();
            event.setUser(user);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload == null ? Collections.emptyMap() : payload));
            auditEventRepository.save(event);
        } catch (Exception ignored) {
        }
    }

    public List<AuditEvent> recentEvents(UUID userId) {
        return auditEventRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }
}
