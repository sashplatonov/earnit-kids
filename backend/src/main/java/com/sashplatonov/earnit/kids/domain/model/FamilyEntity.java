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
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "families")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "family_id", nullable = false, unique = true)
    private String familyId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "admin_password", nullable = false)
    private String adminPassword;

    @Column(name = "is_blocked")
    @Builder.Default
    private boolean blocked = false;

    @Column(name = "is_verified")
    private boolean verified;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "last_selected_child_id")
    private Integer lastSelectedChildId;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private Instant resetTokenExpiresAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "last_activity")
    @Builder.Default
    private Instant lastActivity = Instant.now();

    public void verify() {
        this.verified = true;
        this.verificationToken = null;
    }

    public void setResetToken(String token, Instant expiresAt) {
        this.resetToken = token;
        this.resetTokenExpiresAt = expiresAt;
    }

    public void clearResetToken() {
        this.resetToken = null;
        this.resetTokenExpiresAt = null;
    }
}
