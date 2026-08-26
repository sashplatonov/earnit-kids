package com.sashplatonov.earnit.kids.family.domain.model.catalog;

import java.io.Serializable;
import java.util.Objects;

public class CatalogItemTranslationId implements Serializable {
    private Long catalogItem;
    private String localeCode;

    public CatalogItemTranslationId() {
    }

    public CatalogItemTranslationId(Long catalogItem, String localeCode) {
        this.catalogItem = catalogItem;
        this.localeCode = localeCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogItemTranslationId that)) {
            return false;
        }
        return Objects.equals(catalogItem, that.catalogItem)
            && Objects.equals(localeCode, that.localeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalogItem, localeCode);
    }
}
