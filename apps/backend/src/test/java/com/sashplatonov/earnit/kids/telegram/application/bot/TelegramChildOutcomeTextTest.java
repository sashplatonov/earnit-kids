package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramChildOutcomeTextTest {

    @Test
    void directTaskAction_usesChildCopy() {
        ApplicationOutboxEventEntity event = event(ApplicationOutboxEventType.TASK_APPROVED, null, 5, 20);
        TelegramChildOutcomeText text = new TelegramChildOutcomeText(mock(PurchaseRequestRepository.class),
            mock(ShopItemRepository.class));
        assertThat(text.text(event)).contains("5").contains("20");
    }

    @Test
    void directRewardAction_usesChildCopy() {
        ApplicationOutboxEventEntity event = event(ApplicationOutboxEventType.REWARD_PURCHASED, null, -4, 16);
        TelegramChildOutcomeText text = new TelegramChildOutcomeText(mock(PurchaseRequestRepository.class),
            mock(ShopItemRepository.class));
        assertThat(text.text(event)).contains("16");
    }

    @Test
    void requestWithTaskName_usesRequestSpecificCopy() {
        PurchaseRequestRepository requests = mock(PurchaseRequestRepository.class);
        PurchaseRequestEntity request = mock(PurchaseRequestEntity.class);
        when(request.getTaskName()).thenReturn("Clean room");
        when(requests.findByIdOptional(9L)).thenReturn(java.util.Optional.of(request));
        ApplicationOutboxEventEntity event = event(ApplicationOutboxEventType.TASK_APPROVED, 9L, 10, 30);
        TelegramChildOutcomeText text = new TelegramChildOutcomeText(requests, mock(ShopItemRepository.class));
        assertThat(text.text(event)).contains("Clean room");
    }

    @Test
    void missingRequest_usesGenericOutcome() throws Exception {
        PurchaseRequestRepository requests = mock(PurchaseRequestRepository.class);
        when(requests.findByIdOptional(9L)).thenReturn(java.util.Optional.empty());
        ApplicationOutboxEventEntity event = event(ApplicationOutboxEventType.REWARD_REJECTED, 9L, 0, null);
        TelegramChildOutcomeText text = new TelegramChildOutcomeText(requests, mock(ShopItemRepository.class));
        TelegramLocaleContext.with(FamilyLocale.en, () ->
            assertThat(text.text(event)).contains("Reward rejected"));
    }

    @Test
    void requestWithItem_usesShopTitle() {
        PurchaseRequestRepository requests = mock(PurchaseRequestRepository.class);
        ShopItemRepository items = mock(ShopItemRepository.class);
        PurchaseRequestEntity request = mock(PurchaseRequestEntity.class);
        ShopItemEntity item = mock(ShopItemEntity.class);
        when(request.getItemId()).thenReturn(11L);
        when(requests.findByIdOptional(9L)).thenReturn(java.util.Optional.of(request));
        when(items.findByIdOptional(11L)).thenReturn(java.util.Optional.of(item));
        when(item.getName()).thenReturn("Movie night");
        TelegramChildOutcomeText text = new TelegramChildOutcomeText(requests, items);
        assertThat(text.text(event(ApplicationOutboxEventType.REWARD_APPROVED, 9L, -5, 10)))
            .contains("Movie night");
    }

    @Test
    void genericOutcome_includesBalanceDelta() {
        TelegramChildOutcomeText text = new TelegramChildOutcomeText(mock(PurchaseRequestRepository.class),
            mock(ShopItemRepository.class));
        assertThat(text.text(event(ApplicationOutboxEventType.BALANCE_ADJUSTED, 9L, 5, 25)))
            .contains("+5").contains("25");
    }

    private static ApplicationOutboxEventEntity event(ApplicationOutboxEventType type, Long requestId,
                                                        int delta, Integer balance) {
        ApplicationOutboxEventEntity event = mock(ApplicationOutboxEventEntity.class);
        when(event.getEventType()).thenReturn(type);
        when(event.getRequestId()).thenReturn(requestId);
        when(event.getCoinDelta()).thenReturn(delta);
        when(event.getResultingBalance()).thenReturn(balance);
        return event;
    }
}
