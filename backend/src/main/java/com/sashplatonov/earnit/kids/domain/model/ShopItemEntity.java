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
@Table(name = "shop_items")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "family_id", nullable = false)
    private int familyId;

    @Column(name = "child_id", nullable = false)
    private int childId;

    @Column(name = "item_id", nullable = false)
    private long itemId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price")
    private int price;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "frequency")
    private String frequency;

    @Column(name = "money_limit")
    private Integer moneyLimit;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
