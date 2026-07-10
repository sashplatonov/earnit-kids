package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.sashplatonov.earnit.kids.repository.command.ShopItemUpsertCommand;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ShopItemRepository implements PanacheRepositoryBase<ShopItemEntity, Long> {
    private final SlowOperationDiagnostics slowOperationDiagnostics;

    public List<ShopItemEntity> findByFamilyAndChildAndItemIds(int familyDbId, Collection<Integer> childIds,
                                                               Collection<Long> itemIds) {
        if (childIds == null || childIds.isEmpty() || itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return find("familyId = ?1 AND childId IN ?2 AND itemId IN ?3 ORDER BY id DESC",
            familyDbId, childIds, itemIds).list();
    }

    public List<ShopItemEntity> getShopItems(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getShopItems",
            () -> list("childId = ?1 AND deleted = false ORDER BY id ASC", childId),
            "childId",
            String.valueOf(childId)
        );
    }

    public List<ShopItemEntity> getShopItemsForFamily(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getShopItemsForFamily",
            () -> list("familyId = ?1 AND deleted = false ORDER BY id ASC", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public void markAllShopItemsDeleted(int childId) {
        update("deleted = true where childId = ?1", childId);
    }

    @Transactional
    public boolean upsertShopItem(ShopItemUpsertCommand command) {
        Optional<ShopItemEntity> existing = find("childId = ?1 AND itemId = ?2", command.childId(), command.itemId())
            .firstResultOptional();
        if (existing.isPresent()) {
            ShopItemEntity shopItem = existing.get();
            shopItem.setName(command.name());
            shopItem.setPrice(command.price());
            shopItem.setGroupName(command.groupName());
            shopItem.setFrequency(command.frequency());
            shopItem.setComment(command.comment());
            shopItem.setMoneyLimit(command.moneyLimit());
            shopItem.setActive(command.active());
            shopItem.setDeleted(command.deleted());
        } else {
            persist(ShopItemEntity.builder()
                .familyId(command.familyDbId())
                .childId(command.childId())
                .itemId(command.itemId())
                .name(command.name())
                .price(command.price())
                .groupName(command.groupName())
                .frequency(command.frequency())
                .comment(command.comment())
                .moneyLimit(command.moneyLimit())
                .active(command.active())
                .deleted(command.deleted())
                .build());
        }
        return true;
    }

    @Transactional
    public boolean softDeleteShopItem(int childId, long itemId) {
        return find("childId = ?1 AND itemId = ?2", childId, itemId)
            .firstResultOptional()
            .map(shopItem -> {
                shopItem.setDeleted(true);
                return true;
            })
            .orElse(false);
    }
}
