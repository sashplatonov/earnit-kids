package com.sashplatonov.earnit.kids.family.domain.model.history;

import com.sashplatonov.earnit.kids.platform.domain.persistence.CreatedAtEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "history")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoryEntryEntity extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "family_id", nullable = false)
    private int familyId;

    @Column(name = "child_id", nullable = false)
    private int childId;

    @Column(name = "external_id")
    private Long externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private HistoryEntryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    @lombok.Builder.Default
    private LedgerReason reason = LedgerReason.MANUAL_ADJUSTMENT;

    @Column(name = "delta", nullable = false)
    private int delta;

    @Column(name = "reverses_entry_id")
    private Long reversesEntryId;

    @Column(name = "amount")
    private int amount;

    @Column(name = "description")
    private String description;

    @Column(name = "money_amount")
    private int moneyAmount;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "comment")
    private String comment;

    @PrePersist
    void initializeLedgerFields() {
        if (reason == null) {
            reason = LedgerReason.MANUAL_ADJUSTMENT;
        }
        if (delta == 0 && amount != 0) {
            delta = type == HistoryEntryType.spend ? -Math.abs(amount) : Math.abs(amount);
        }
    }
}
