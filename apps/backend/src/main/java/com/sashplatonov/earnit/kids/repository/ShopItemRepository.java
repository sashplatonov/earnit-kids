package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class ShopItemRepository implements PanacheRepositoryBase<ShopItemEntity, Long> {

    public List<ShopItemEntity> findByFamilyAndChildAndItemIds(int familyDbId, Collection<Integer> childIds,
                                                               Collection<Long> itemIds) {
        if (childIds == null || childIds.isEmpty() || itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return find("familyId = ?1 AND childId IN ?2 AND itemId IN ?3 ORDER BY id DESC",
            familyDbId, childIds, itemIds).list();
    }
}
