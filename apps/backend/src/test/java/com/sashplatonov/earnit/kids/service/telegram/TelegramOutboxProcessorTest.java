package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus;
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
    private final TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
    private final TelegramNotificationComposer composer =
        new TelegramNotificationComposer(children, requests, shopItems, callbacks);
    private final TelegramOutboxProcessor processor = new TelegramOutboxProcessor(
        planner, deliveries, events, requests, api, config, null, composer);

    @Test
    void successfulDeliveryIsTerminalAndUsesServerBalance() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(3L).eventId(8L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(8L).eventType(ApplicationOutboxEventType.TASK_APPROVED).familyId(1).childId(2)
            .requestId(8L).coinDelta(20).resultingBalance(145).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(8L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        when(requests.findByIdOptional(8L)).thenReturn(Optional.of(
            PurchaseRequestEntity.builder().id(8L).taskName("Утренний старт").coins(20).build()));
        when(callbacks.signNavigation(anyString())).thenAnswer(invocation ->
            "nav." + invocation.getArgument(0, String.class) + ".signed");

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).sendMessage(77L,
            "🎉 Утренний старт одобрен\n\n🟢 🟡 +20 монет\nБаланс: 145",
            List.of(
                TelegramBotApiClient.InlineButton.callback("✅ Мои задания", "nav.tasks.signed"),
                TelegramBotApiClient.InlineButton.callback("🎁 Награды", "nav.rewards.signed")));
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
        verify(api).sendMessage(77L, "👧 Aliska выполнила:\n\nУтренний старт\n🟢 🟡 +1 монета",
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

    @Test
    void directParentTaskCompletionSendsRussianNotificationToChild() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(10L).eventId(10L).recipientIdentityId(5).chatId(78L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(10L).eventType(ApplicationOutboxEventType.TASK_APPROVED).familyId(1).childId(2)
            .requestId(null).coinDelta(20).resultingBalance(145).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(10L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        when(callbacks.signNavigation(anyString())).thenAnswer(invocation ->
            "nav." + invocation.getArgument(0, String.class) + ".signed");

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).sendMessage(78L,
            "🎉 Родитель выполнил задание за тебя\n\n🟢 🟡 +20 монет\nБаланс: 145",
            List.of(
                TelegramBotApiClient.InlineButton.callback("✅ Мои задания", "nav.tasks.signed"),
                TelegramBotApiClient.InlineButton.callback("🎁 Награды", "nav.rewards.signed")));
    }

    @Test
    void directParentRewardGrantSendsRussianNotificationToChild() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(11L).eventId(11L).recipientIdentityId(5).chatId(78L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(11L).eventType(ApplicationOutboxEventType.REWARD_PURCHASED).familyId(1).childId(2)
            .requestId(null).coinDelta(-50).resultingBalance(95).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(11L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).sendMessage(78L,
            "🎉 Родитель выдал награду\n\n🔴 🟡 -50 монет\nБаланс: 95",
            List.of());
    }

    @Test
    void resolvedRequestEditsMessageAndDropsButtons() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(12L).eventId(12L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .messageId(19L).nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(12L).eventType(ApplicationOutboxEventType.REQUEST_RESOLVED).familyId(1).childId(2)
            .requestId(8L).resolutionStatus(RequestResolutionStatus.approved)
            .resolutionTitle("Утренний старт").createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(12L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).editMessageText(77L, 19L, "Утренний старт\n✅ Одобрено", List.of());
        verify(api, never()).sendMessage(any(Long.class), any(String.class), any());
    }

    @Test
    void resolvedRequestWithoutMessageIdIsSkipped() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(13L).eventId(13L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(13L).eventType(ApplicationOutboxEventType.REQUEST_RESOLVED).familyId(1).childId(2)
            .requestId(8L).resolutionStatus(RequestResolutionStatus.rejected)
            .resolutionTitle("Утренний старт").createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(13L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SKIPPED");
        verify(api, never()).editMessageText(any(Long.class), any(Long.class), any(String.class), any());
    }

    @Test
    void resolvedMessageAlreadyAbsentIsNoOpSuccess() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(14L).eventId(14L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .messageId(19L).nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(14L).eventType(ApplicationOutboxEventType.REQUEST_RESOLVED).familyId(1).childId(2)
            .requestId(8L).resolutionStatus(RequestResolutionStatus.cancelled)
            .resolutionTitle("Утренний старт").createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(14L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        doThrow(new TelegramApiException(400, "message to edit not found", 0))
            .when(api).editMessageText(any(Long.class), any(Long.class), any(String.class), any());

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void resolvedTransientErrorRetries() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(15L).eventId(15L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .messageId(19L).nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(15L).eventType(ApplicationOutboxEventType.REQUEST_RESOLVED).familyId(1).childId(2)
            .requestId(8L).resolutionStatus(RequestResolutionStatus.deleted)
            .resolutionTitle("Утренний старт").createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(15L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        doThrow(new TelegramApiException(500, "internal error", 0))
            .when(api).editMessageText(any(Long.class), any(Long.class), any(String.class), any());

        assertThat(processor.process(now)).isZero();
        assertThat(delivery.getStatus()).isEqualTo("PENDING");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfter(now);
    }

    @Test
    void requestAlreadyFinalBeforeSendSkipsActionableMessage() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(16L).eventId(16L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(16L).eventType(ApplicationOutboxEventType.TASK_REQUEST_CREATED).familyId(1).childId(2)
            .requestId(8L).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(16L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        when(requests.findByIdOptional(8L)).thenReturn(Optional.of(
            PurchaseRequestEntity.builder().id(8L).taskName("Утренний старт")
                .status(PurchaseRequestStatus.approved).coins(1).build()));

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SKIPPED");
        verify(api, never()).sendMessage(any(Long.class), any(String.class), any());
    }

    @Test
    void requestResolvedBetweenPreCheckAndSendIsEditedAfterSend() throws Exception {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
            .id(17L).eventId(17L).recipientIdentityId(5).chatId(77L).status("PENDING")
            .nextAttemptAt(now).build();
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .id(17L).eventType(ApplicationOutboxEventType.TASK_REQUEST_CREATED).familyId(1).childId(2)
            .requestId(8L).createdAt(now).build();
        when(deliveries.findDue(eq(now), any(Instant.class))).thenReturn(List.of(delivery));
        when(events.findById(17L)).thenReturn(event);
        when(config.outboxMaxAttempts()).thenReturn(5);
        when(api.sendMessage(any(Long.class), any(String.class), any())).thenReturn(19L);
        // EXPLAIN: First read sees pending, second read (post-send recheck) sees approved.
        when(requests.findByIdOptional(8L))
            .thenReturn(Optional.of(PurchaseRequestEntity.builder().id(8L).taskName("Утренний старт")
                .status(PurchaseRequestStatus.pending).coins(1).build()))
            .thenReturn(Optional.of(PurchaseRequestEntity.builder().id(8L).taskName("Утренний старт")
                .status(PurchaseRequestStatus.approved).coins(1).build()));
        when(children.findByIdOptional(2)).thenReturn(Optional.of(
            ChildEntity.builder().id(2).name("Aliska").build()));

        assertThat(processor.process(now)).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(api).sendMessage(77L, "👧 Aliska выполнила:\n\nУтренний старт\n🟢 🟡 +1 монета",
            List.of(
                TelegramBotApiClient.InlineButton.callback("👍 Одобрить", "parent.request.approve.2.8"),
                TelegramBotApiClient.InlineButton.callback("👎 Отклонить", "parent.request.reject.2.8")));
        verify(api).editMessageText(77L, 19L, "Утренний старт\n✅ Одобрено", List.of());
    }
}
