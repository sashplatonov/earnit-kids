package com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CatalogItemRepository implements PanacheRepositoryBase<CatalogItemEntity, Long> {
    public List<CatalogItemEntity> findActiveOrdered() {
        return list("active = true ORDER BY itemType, sortOrder, externalId");
    }
}
