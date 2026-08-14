package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramDeliveryPlannerTest {
    @Test
    void disabledNotificationsCreateTerminalDeliveryAndCompleteEvent() {
        ApplicationOutboxEventRepository events = mock(ApplicationOutboxEventRepository.class);
        TelegramDeliveryRepository deliveries = mock(TelegramDeliveryRepository.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.notificationsEnabled()).thenReturn(false);
        when(config.rolloutFamilyId()).thenReturn(Optional.empty());
        TelegramFeatureGate gate = new TelegramFeatureGate(config);
        TelegramDeliveryPlanner planner = new TelegramDeliveryPlanner(events, deliveries, identities, families, gate,
            new TelegramObservability(new SimpleMeterRegistry()));
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder().id(1L).familyId(2).childId(3)
            .eventType(ApplicationOutboxEventType.TASK_APPROVED).createdAt(Instant.EPOCH).build();
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder().id(4).telegramUserId(5L).active(true).build();
        AtomicReference<TelegramDeliveryEntity> created = new AtomicReference<>();
        when(events.findPlanningCandidates(Instant.parse("2026-08-14T09:58:00Z"))).thenReturn(List.of(event));
        when(families.findFamilyIdByDbId(2)).thenReturn(Optional.of("family-2"));
        when(identities.findActiveChild(3)).thenReturn(Optional.of(identity));
        when(deliveries.findByEventAndRecipient(1L, 4)).thenReturn(Optional.empty());
        when(deliveries.findByEvent(1L)).thenAnswer(invocation -> List.of(created.get()));
        org.mockito.Mockito.doAnswer(invocation -> { created.set(invocation.getArgument(0)); return null; }).when(deliveries).persist(org.mockito.ArgumentMatchers.any(TelegramDeliveryEntity.class));

        assertThat(planner.planDueEvents(Instant.parse("2026-08-14T10:00:00Z"))).isEqualTo(1);
        assertThat(created.get().getStatus()).isEqualTo("SKIPPED_DISABLED");
        assertThat(event.getPlanningStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void stagedRolloutPlansNotificationsForTheAllowedPublicFamilyId() {
        ApplicationOutboxEventRepository events = mock(ApplicationOutboxEventRepository.class);
        TelegramDeliveryRepository deliveries = mock(TelegramDeliveryRepository.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.notificationsEnabled()).thenReturn(true);
        when(config.botToken()).thenReturn(Optional.of("token"));
        when(config.rolloutFamilyId()).thenReturn(Optional.of("family-in-rollout"));
        TelegramDeliveryPlanner planner = new TelegramDeliveryPlanner(events, deliveries, identities, families,
            new TelegramFeatureGate(config), new TelegramObservability(new SimpleMeterRegistry()));
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder().id(1L).familyId(2).childId(3)
            .eventType(ApplicationOutboxEventType.TASK_APPROVED).createdAt(Instant.EPOCH).build();
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder().id(4).telegramUserId(5L).active(true).build();
        AtomicReference<TelegramDeliveryEntity> created = new AtomicReference<>();
        when(events.findPlanningCandidates(Instant.parse("2026-08-14T09:58:00Z"))).thenReturn(List.of(event));
        when(families.findFamilyIdByDbId(2)).thenReturn(Optional.of("family-in-rollout"));
        when(identities.findActiveChild(3)).thenReturn(Optional.of(identity));
        when(deliveries.findByEventAndRecipient(1L, 4)).thenReturn(Optional.empty());
        when(deliveries.findByEvent(1L)).thenAnswer(invocation -> List.of(created.get()));
        org.mockito.Mockito.doAnswer(invocation -> {
            created.set(invocation.getArgument(0));
            return null;
        }).when(deliveries).persist(org.mockito.ArgumentMatchers.any(TelegramDeliveryEntity.class));

        assertThat(planner.planDueEvents(Instant.parse("2026-08-14T10:00:00Z"))).isEqualTo(1);
        assertThat(created.get().getStatus()).isEqualTo("PENDING");
        assertThat(event.getPlanningStatus()).isEqualTo("PLANNED");
    }
}
