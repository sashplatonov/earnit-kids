package com.sashplatonov.earnit.kids.platform.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SecurityAuditWriter {
    private final SecurityAuditEventRepository repository;

    @Inject
    public SecurityAuditWriter(SecurityAuditEventRepository repository) {
        this.repository = repository;
    }

    public void write(Integer familyId, Integer actorParentAccountId, String actorEmail,
                      String targetEmail, String eventType, String reasonCode) {
        repository.persist(SecurityAuditEventEntity.builder()
            .familyId(familyId).actorParentAccountId(actorParentAccountId).actorEmail(actorEmail)
            .targetEmail(targetEmail).eventType(eventType).reasonCode(reasonCode)
            .requestCorrelationId(UUID.randomUUID().toString()).createdAt(Instant.now()).build());
    }
}
