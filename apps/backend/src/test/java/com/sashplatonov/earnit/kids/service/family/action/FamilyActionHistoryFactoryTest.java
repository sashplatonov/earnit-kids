package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyActionHistoryFactoryTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final FamilyActionHistoryFactory factory = new FamilyActionHistoryFactory(() -> NOW);

    @Test
    void buildsTaskAndShopHistory() {
        var task = TaskEntity.builder().taskId(4).name("Read").coins(5).groupName("Home").comment("daily").build();
        var item = ShopItemEntity.builder().itemId(8).name("Toy").price(9).moneyLimit(2).groupName("Fun").build();

        assertThat(factory.buildTaskHistory(1, 2, task))
            .extracting(e -> e.getType(), e -> e.getAmount(), e -> e.getRelatedId())
            .containsExactly(HistoryEntryType.earn, 5, 4L);
        assertThat(factory.buildShopHistory(1, 2, item))
            .extracting(e -> e.getType(), e -> e.getAmount(), e -> e.getMoneyAmount())
            .containsExactly(HistoryEntryType.spend, 9, 2);
    }

    @Test
    void buildsRequestHistoryForShopAndTaskAndAdjustments() {
        var shopRequest = PurchaseRequestEntity.builder().childId(2).requestType(PurchaseRequestType.shop_purchase)
            .coins(9).itemId(8L).createdAt(NOW).build();
        var item = ShopItemEntity.builder().itemId(8).name("Toy").build();
        assertThat(factory.buildRequestHistory(1, shopRequest, Optional.of(item), Optional.empty()).getType())
            .isEqualTo(HistoryEntryType.spend);

        var taskRequest = PurchaseRequestEntity.builder().childId(2).requestType(PurchaseRequestType.earn)
            .coins(4).taskId(4L).taskName("Read").build();
        assertThat(factory.buildRequestHistory(1, taskRequest, Optional.empty(), Optional.empty()).getType())
            .isEqualTo(HistoryEntryType.earn);
        assertThat(factory.buildAdjustmentHistory(1, 2, 5, "  credit  ").getDescription()).isEqualTo("credit");
        assertThat(factory.buildAdjustmentHistory(1, 2, -5, "").getType()).isEqualTo(HistoryEntryType.spend);
        assertThat(factory.nextExternalId()).isEqualTo(NOW.toEpochMilli());
    }
}
