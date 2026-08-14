package com.sashplatonov.earnit.kids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "application_outbox_events")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationOutboxEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false) private ApplicationOutboxEventType eventType;
    @Column(name = "family_id", nullable = false) private Integer familyId;
    @Column(name = "child_id", nullable = false) private Integer childId;
    @Column(name = "request_id") private Long requestId;
    @Column(name = "coin_delta", nullable = false) private int coinDelta;
    @Column(name = "resulting_balance") private Integer resultingBalance;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "planning_claimed_at") private Instant planningClaimedAt;
    @Column(name = "planning_completed_at") private Instant planningCompletedAt;
    @Column(name = "planning_status") private String planningStatus;
}
