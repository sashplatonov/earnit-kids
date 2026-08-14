package com.sashplatonov.earnit.kids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "telegram_security_audit_events")
@Getter @Setter @SuperBuilder @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramSecurityAuditEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "family_id") private Integer familyId;
    @Column(name = "child_id") private Integer childId;
    @Column(name = "identity_id") private Integer identityId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "actor_reference") private String actorReference;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
