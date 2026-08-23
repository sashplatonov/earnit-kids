package com.sashplatonov.earnit.kids.family.domain.model.invitation;

import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.platform.domain.persistence.CreatedAtEntity;
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
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "parent_email_invitations")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentEmailInvitationEntity extends CreatedAtEntity {

    public enum Status { pending, accepted, expired, revoked, superseded }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "family_id", nullable = false)
    private Integer familyId;

    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 32)
    private FamilyParentMembershipEntity.Permission permission;

    @Column(name = "token_digest", nullable = false, unique = true, length = 128)
    private String tokenDigest;

    @Column(name = "token_digest_key_id", nullable = false, length = 64)
    private String tokenDigestKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.pending;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by_email", nullable = false, length = 320)
    private String invitedByEmail;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;
}
