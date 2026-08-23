package com.sashplatonov.earnit.kids.identity.domain.model;

import com.sashplatonov.earnit.kids.platform.domain.persistence.CreatedAtEntity;
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
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "oauth_invitation_continuations")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthInvitationContinuationEntity extends CreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "invitation_id", nullable = false)
    private Integer invitationId;

    @Column(name = "nonce_digest", nullable = false, unique = true, length = 128)
    private String nonceDigest;

    @Column(name = "browser_binding_digest", nullable = false, length = 128)
    private String browserBindingDigest;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "post_login_path", nullable = false, length = 256)
    @Builder.Default
    private String postLoginPath = "/invite/parent";

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "verified_email", length = 320)
    private String verifiedEmail;
}
