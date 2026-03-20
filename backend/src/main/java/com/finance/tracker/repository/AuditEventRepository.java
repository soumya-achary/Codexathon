package com.finance.tracker.repository;

import com.finance.tracker.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
