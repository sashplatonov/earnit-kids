package com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemTranslationEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemTranslationId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CatalogItemTranslationRepository
    implements PanacheRepositoryBase<CatalogItemTranslationEntity, CatalogItemTranslationId> {
    public List<CatalogItemTranslationEntity> findForItemsAndLocales(
        List<Long> itemIds, List<String> localeCodes) {
        if (itemIds.isEmpty() || localeCodes.isEmpty()) {
            return List.of();
        }
        return list("catalogItem.id in ?1 and localeCode in ?2", itemIds, localeCodes);
    }
}
