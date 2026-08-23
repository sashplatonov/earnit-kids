package com.sashplatonov.earnit.kids.platform.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "security_audit_events")
@Getter @Setter @Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityAuditEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "family_id") private Integer familyId;
    @Column(name = "actor_parent_account_id") private Integer actorParentAccountId;
    @Column(name = "actor_email", length = 320) private String actorEmail;
    @Column(name = "target_email", length = 320) private String targetEmail;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(name = "reason_code", nullable = false, length = 64) private String reasonCode;
    @Column(name = "request_correlation_id", length = 64) private String requestCorrelationId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
