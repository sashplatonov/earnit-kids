package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramOutboxProcessorTest {
    private final TelegramDeliveryPlanner planner = mock(TelegramDeliveryPlanner.class);
    private final TelegramDeliveryRepository deliveries = mock(TelegramDeliveryRepository.class);
    private final ApplicationOutboxEventRepository events = mock(ApplicationOutboxEventRepository.class);
    private final TelegramBotApiClient api = mock(TelegramBotApiClient.class);
    private final TelegramConfig config = mock(TelegramConfig.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final PurchaseRequestRepository requests = mock(PurchaseRequestRepository.class);
    private final ShopItemRepository shopItems = mock(ShopItemRepository.class);
    private final TelegramOutboxProcessor processor = new TelegramOutboxProcessor(
        planner, deliveries, events, api, config, children, requests, shopItems);

    @Test
    void successfulDeliveryIsTerminalAndUsesServerBalance() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(3L).eventId(8L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(8L).eventType(ApplicationOutboxEventType.TASK_APPROVED).familyId(1).childId(2)
            .coinDelta(20).resultingBalance(145).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(8L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).sendMessage(77L, "✅ Task approved\n+20 🪙\nBalance: 145 🪙", List.of());
    }

    @Test
    void requestNotificationCarriesApproveAndRejectButtons() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(9L).eventId(9L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(9L).eventType(ApplicationOutboxEventType.TASK_REQUEST_CREATED).familyId(1).childId(2)
            .requestId(8L).coinDelta(0).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(9L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        when(requests.findByIdOptional(8L)).thenReturn(Optional.of(
            PurchaseRequestEntity.builder().id(8L).taskName("Утренний старт").coins(1).build()));
        when(children.findByIdOptional(2)).thenReturn(Optional.of(
            ChildEntity.builder().id(2).name("Aliska").build()));

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).sendMessage(77L, "👧 Aliska выполнила:\n\nУтренний старт\n🪙 +1 монета",
            List.of(
                TelegramBotApiClient.InlineButton.callback("👍 Одобрить", "parent.request.approve.2.8"),
                TelegramBotApiClient.InlineButton.callback("👎 Отклонить", "parent.request.reject.2.8")));
    }

    @Test
    void transportFailureBacksOffWithoutChangingDomainEvent() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .eventId(8L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(8L).eventType(ApplicationOutboxEventType.BALANCE_ADJUSTED).familyId(1).childId(2)
            .coinDelta(30).resultingBalance(125).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(8L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        doThrow(new IllegalStateException("telegram unavailable"))
            .when(api).sendMessage(any(Long.class), any(String.class), any());

        assertThat(processor.process(now)).isZero();
        assertThat(delivery.getStatus()).isEqualTo("PENDING");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfter(now);
        assertThat(event.getResultingBalance()).isEqualTo(125);
    }
}
