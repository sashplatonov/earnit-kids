package com.sashplatonov.earnit.kids.family.domain.model.membership;

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
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "family_parent_memberships")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyParentMembershipEntity extends CreatedAtEntity {

    public enum Permission {
        viewer, editor, family_admin
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "parent_account_id", nullable = false)
    private Integer parentAccountId;

    @Column(name = "family_id", nullable = false)
    private Integer familyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "permission", nullable = false)
    @Builder.Default
    private Permission permission = Permission.viewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.active;

    @Column(name = "invited_by_email")
    private String invitedByEmail;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "invited_at")
    private Instant invitedAt;
}
