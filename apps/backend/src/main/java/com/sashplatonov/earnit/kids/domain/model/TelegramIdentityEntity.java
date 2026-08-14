package com.sashplatonov.earnit.kids.domain.model;

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
@Table(name = "telegram_identities")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramIdentityEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "family_id") private Integer familyId;
    @Column(name = "parent_account_id") private Integer parentAccountId;
    @Column(name = "child_id") private Integer childId;
    @Column(name = "telegram_user_id", nullable = false) private Long telegramUserId;
    @Column(nullable = false) private String role;
    @Builder.Default
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt;
    @Column(name = "unlinked_at") private Instant unlinkedAt;
}
