package com.sashplatonov.earnit.kids.telegram.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "telegram_parent_link_challenges")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramParentLinkChallengeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "parent_account_id", nullable = false) private Integer parentAccountId;
    @Column(name = "family_id", nullable = false) private Integer familyId;
    @Column(name = "secret_digest", nullable = false, unique = true) private String secretDigest;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Builder.Default
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
