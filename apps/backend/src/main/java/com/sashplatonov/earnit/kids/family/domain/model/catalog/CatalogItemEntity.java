package com.sashplatonov.earnit.kids.family.domain.model.catalog;

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

@Entity
@Table(name = "catalog_items")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private CatalogItemType itemType;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "comment_en")
    private String commentEn;

    @Column(name = "comment_ru")
    private String commentRu;

    @Column(name = "group_key", nullable = false)
    private String groupKey;

    @Column(name = "group_name_en", nullable = false)
    private String groupNameEn;

    @Column(name = "group_name_ru", nullable = false)
    private String groupNameRu;

    @Column(name = "semantic_graphic_key", nullable = false)
    private String semanticGraphicKey;

    @Column(name = "frequency_limit", nullable = false)
    private int frequencyLimit;

    @Column(name = "frequency_period", nullable = false)
    private String frequencyPeriod;

    @Column(name = "min_age", nullable = false)
    private int minAge;

    @Column(name = "max_age", nullable = false)
    private int maxAge;

    @Column(name = "difficulty", nullable = false)
    private String difficulty;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "coins")
    private Integer coins;

    @Column(name = "price")
    private Integer price;
}
