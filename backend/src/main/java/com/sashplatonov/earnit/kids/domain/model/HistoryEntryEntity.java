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

import java.time.Instant;

@Entity
@Table(name = "history")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoryEntryEntity {

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

    @Column(name = "type", nullable = false)
    private String type;

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

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
