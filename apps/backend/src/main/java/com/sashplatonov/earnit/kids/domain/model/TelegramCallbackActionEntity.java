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
@Table(name = "telegram_callback_actions")
@Getter @Setter @SuperBuilder @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramCallbackActionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "family_id", nullable = false) private Integer familyId;
    @Column(name = "identity_id") private Integer identityId;
    @Column(nullable = false) private String action;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(name = "secret_digest", nullable = false, unique = true) private String secretDigest;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
