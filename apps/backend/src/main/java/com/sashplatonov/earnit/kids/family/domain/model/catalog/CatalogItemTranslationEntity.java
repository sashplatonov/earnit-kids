package com.sashplatonov.earnit.kids.family.domain.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalog_item_translations")
@IdClass(CatalogItemTranslationId.class)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogItemTranslationEntity {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id", nullable = false)
    private CatalogItemEntity catalogItem;

    @Id
    @Column(name = "locale_code", nullable = false, length = 16)
    private String localeCode;

    @Column(nullable = false, length = 500)
    private String title;

    @Column
    private String comment;

    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;
}
