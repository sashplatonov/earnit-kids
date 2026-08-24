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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "family_admin_transfer_requests")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyAdminTransferRequestEntity extends CreatedAtEntity {

    public enum Status {
        pending, accepted, declined, cancelled
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "family_id", nullable = false)
    private Integer familyId;

    @Column(name = "actor_membership_id", nullable = false)
    private Integer actorMembershipId;

    @Column(name = "target_membership_id", nullable = false)
    private Integer targetMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Status status = Status.pending;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
