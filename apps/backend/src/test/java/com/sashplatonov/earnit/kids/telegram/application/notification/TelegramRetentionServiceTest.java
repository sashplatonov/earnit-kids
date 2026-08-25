package com.sashplatonov.earnit.kids.telegram.application.notification;

import com.sashplatonov.earnit.kids.telegram.config.TelegramRetentionConfig;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramChildInvitationRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramWebhookUpdateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class TelegramRetentionServiceTest {
    private final TelegramChildInvitationRepository invitations = mock(TelegramChildInvitationRepository.class);
    private final TelegramCallbackActionRepository callbacks = mock(TelegramCallbackActionRepository.class);
    private final TelegramWebhookUpdateRepository webhookUpdates = mock(TelegramWebhookUpdateRepository.class);
    private final TelegramDeliveryRepository deliveries = mock(TelegramDeliveryRepository.class);
    private final ApplicationOutboxEventRepository outboxEvents = mock(ApplicationOutboxEventRepository.class);
    private final TelegramSecurityAuditEventRepository auditEvents = mock(TelegramSecurityAuditEventRepository.class);
    private final TelegramRetentionConfig config = mock(TelegramRetentionConfig.class);
    private TelegramRetentionService service;

    @BeforeEach
    void setUp() {
        when(config.retentionBatchSize()).thenReturn(2);
        when(config.inviteRetentionDays()).thenReturn(30);
        when(config.callbackRetentionDays()).thenReturn(20);
        when(config.webhookUpdateRetentionDays()).thenReturn(10);
        when(config.deliveryRetentionDays()).thenReturn(7);
        when(config.outboxRetentionDays()).thenReturn(5);
        when(config.auditRetentionDays()).thenReturn(365);
        service = new TelegramRetentionService(invitations, callbacks, webhookUpdates, deliveries,
                outboxEvents, auditEvents, config);
    }

    @Test
    void dryRun_reportsAllEligibleRows_withoutDeleting() {
        when(invitations.countEligible(org.mockito.ArgumentMatchers.any())).thenReturn(4L);
        when(callbacks.countEligible(org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        when(webhookUpdates.countEligible(org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(deliveries.countEligible(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(outboxEvents.countEligible(org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        when(auditEvents.countEligible(org.mockito.ArgumentMatchers.any())).thenReturn(6L);

        var report = service.dryRun(Instant.parse("2026-08-14T00:00:00Z"));

        assertThat(report).isEqualTo(new TelegramRetentionReport(4, 3, 2, 1, 5, 6));
        verify(invitations, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(callbacks, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(webhookUpdates, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(deliveries, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(outboxEvents, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(auditEvents, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void cleanup_deletesAtMostConfiguredBatch_andProcessesDeliveriesBeforeOutbox() {
        when(invitations.deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(2);
        when(callbacks.deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(1);
        when(webhookUpdates.deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(2);
        when(deliveries.deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(2);
        when(outboxEvents.deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(2);
        when(auditEvents.deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(1);

        var report = service.cleanup(Instant.parse("2026-08-14T00:00:00Z"), false);

        assertThat(report.total()).isEqualTo(10);
        verify(deliveries).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2));
        verify(outboxEvents).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    void cleanup_rejectsNonPositiveRetentionBeforeDeletingRows() {
        when(config.deliveryRetentionDays()).thenReturn(0);

        assertThatThrownBy(() -> service.cleanup(Instant.parse("2026-08-14T00:00:00Z"), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("delivery retention days must be positive");
        verify(invitations, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(callbacks, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(webhookUpdates, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(deliveries, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(outboxEvents, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(auditEvents, never()).deleteEligible(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void scheduledCleanup_startsItsOwnTransaction() throws NoSuchMethodException {
        assertThat(TelegramRetentionService.class.getDeclaredMethod("scheduled")
            .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
