package com.sashplatonov.earnit.kids.platform.webpush;

import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebPushOutboxProcessorTest {
    private final WebPushService service = mock(WebPushService.class);
    private final WebPushDeliveryRepository deliveries = mock(WebPushDeliveryRepository.class);
    private final WebPushSubscriptionRepository subscriptions = mock(WebPushSubscriptionRepository.class);
    private final ApplicationOutboxEventRepository events = mock(ApplicationOutboxEventRepository.class);
    private final WebPushConfig config = mock(WebPushConfig.class);
    private final WebPushOutboxProcessor processor =
        new WebPushOutboxProcessor(service, deliveries, subscriptions, events, config);
    private final Instant now = Instant.parse("2026-08-23T10:00:00Z");

    @Test
    void sendsDueDeliveryAndCompletesTerminalEvent() throws Exception {
        WebPushDeliveryEntity delivery = delivery(1L, 2L);
        WebPushSubscriptionEntity subscription = WebPushSubscriptionEntity.builder().id(2L).build();
        ApplicationOutboxEventEntity event = mock(ApplicationOutboxEventEntity.class);
        when(deliveries.findDue(now, now.minusSeconds(60))).thenReturn(List.of(delivery));
        when(subscriptions.findById(2L)).thenReturn(subscription);
        when(events.findById(1L)).thenReturn(event);
        when(event.getId()).thenReturn(1L);
        when(events.allTransportsTerminal(1L)).thenReturn(true);

        assertThat(processor.process(now)).isEqualTo(1);
        verify(service).send(subscription, delivery);
        assertThat(delivery.getStatus()).isEqualTo("SENT");
        verify(event).setPlanningStatus("COMPLETE");
    }

    @Test
    void missingSubscriptionFailsDelivery() throws Exception {
        WebPushDeliveryEntity delivery = delivery(1L, 2L);
        when(deliveries.findDue(now, now.minusSeconds(60))).thenReturn(List.of(delivery));
        when(subscriptions.findById(2L)).thenReturn(null);

        assertThat(processor.process(now)).isZero();
        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        verify(service, never()).send(any(), any());
    }

    @Test
    void permanentTransportFailureDeletesSubscriptionAndRetryableFailureSchedulesRetry() throws Exception {
        WebPushDeliveryEntity permanent = delivery(1L, 2L);
        WebPushDeliveryEntity retryable = delivery(2L, 3L);
        WebPushSubscriptionEntity first = WebPushSubscriptionEntity.builder().id(2L).build();
        WebPushSubscriptionEntity second = WebPushSubscriptionEntity.builder().id(3L).build();
        when(deliveries.findDue(now, now.minusSeconds(60))).thenReturn(List.of(permanent, retryable));
        when(subscriptions.findById(2L)).thenReturn(first);
        when(subscriptions.findById(3L)).thenReturn(second);
        doThrow(new WebPushTransportException(404)).doThrow(new WebPushTransportException(503))
            .when(service).send(any(), any());
        when(config.maxAttempts()).thenReturn(3);

        assertThat(processor.process(now)).isZero();
        verify(subscriptions).deleteById(2L);
        assertThat(permanent.getStatus()).isEqualTo("FAILED");
        assertThat(retryable.getStatus()).isNull();
        assertThat(retryable.getNextAttemptAt()).isAfter(now);
    }

    private static WebPushDeliveryEntity delivery(long eventId, long subscriptionId) {
        return WebPushDeliveryEntity.builder().eventId(eventId).subscriptionId(subscriptionId)
            .attemptCount(0).build();
    }
}
