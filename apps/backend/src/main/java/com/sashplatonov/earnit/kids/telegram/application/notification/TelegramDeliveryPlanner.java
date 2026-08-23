package com.sashplatonov.earnit.kids.telegram.application.notification;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;

import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class TelegramDeliveryPlanner {
    private static final Duration PLANNING_LEASE = Duration.ofMinutes(2);
    private ApplicationOutboxEventRepository events;
    private TelegramDeliveryRepository deliveries;
    private TelegramIdentityRepository identities;
    private FamilyRepository families;
    private TelegramFeatureGate featureGate;
    private TelegramObservability observability;

    TelegramDeliveryPlanner() {
    }

    @Inject
    TelegramDeliveryPlanner(ApplicationOutboxEventRepository events,
                            TelegramDeliveryRepository deliveries,
                            TelegramIdentityRepository identities,
                            FamilyRepository families,
                            TelegramFeatureGate featureGate,
                            TelegramObservability observability) {
        this.events = events;
        this.deliveries = deliveries;
        this.identities = identities;
        this.families = families;
        this.featureGate = featureGate;
        this.observability = observability;
    }

    @Transactional
    public int planDueEvents(Instant now) {
        int planned = 0;
        for (ApplicationOutboxEventEntity event : events.findPlanningCandidates(now.minus(PLANNING_LEASE))) {
            event.setPlanningClaimedAt(now);
            event.setPlanningStatus("PLANNING");
            boolean notificationsEnabled = families.findFamilyIdByDbId(event.getFamilyId())
                .map(featureGate::areNotificationsEnabled)
                .orElse(false);
            if (event.getEventType() == ApplicationOutboxEventType.REQUEST_RESOLVED) {
                planned += planResolved(event, now, notificationsEnabled);
            } else {
                planned += planRecipients(event, now, notificationsEnabled);
            }
            if (events.allTransportsTerminal(event.getId())) {
                event.setPlanningCompletedAt(now);
                event.setPlanningStatus(status(event));
            } else {
                event.setPlanningStatus("PLANNED");
            }
        }
        return planned;
    }

    private int planResolved(ApplicationOutboxEventEntity event, Instant now, boolean notificationsEnabled) {
        int planned = 0;
        for (TelegramDeliveryEntity original : deliveries.findSentRequestMessages(event.getRequestId())) {
            if (deliveries.findByEventAndRecipient(event.getId(), original.getRecipientIdentityId()).isEmpty()) {
                deliveries.persist(TelegramDeliveryEntity.builder()
                    .eventId(event.getId()).requestId(event.getRequestId())
                    .recipientIdentityId(original.getRecipientIdentityId())
                    .chatId(original.getChatId())
                    .messageId(original.getMessageId())
                    .idempotencyKey(event.getId() + ":" + original.getRecipientIdentityId())
                    .status(notificationsEnabled ? "PENDING" : "SKIPPED_DISABLED")
                    .nextAttemptAt(now).terminalAt(notificationsEnabled ? null : now).build());
                planned++;
                observability.outbox(notificationsEnabled ? "queued" : "skipped_disabled");
            }
        }
        return planned;
    }

    private int planRecipients(ApplicationOutboxEventEntity event, Instant now, boolean notificationsEnabled) {
        int planned = 0;
        List<TelegramIdentityEntity> recipients = recipients(event);
        for (TelegramIdentityEntity identity : recipients) {
            if (deliveries.findByEventAndRecipient(event.getId(), identity.getId()).isEmpty()) {
                TelegramDeliveryEntity delivery = TelegramDeliveryEntity.builder()
                    .eventId(event.getId()).requestId(event.getRequestId())
                    .recipientIdentityId(identity.getId())
                    .chatId(identity.getTelegramUserId())
                    .idempotencyKey(event.getId() + ":" + identity.getId())
                    .status(notificationsEnabled ? "PENDING" : "SKIPPED_DISABLED")
                    .nextAttemptAt(now).terminalAt(notificationsEnabled ? null : now).build();
                deliveries.persist(delivery);
                planned++;
                observability.outbox(notificationsEnabled ? "queued" : "skipped_disabled");
            }
        }
        return planned;
    }

    private List<TelegramIdentityEntity> recipients(ApplicationOutboxEventEntity event) {
        if (isParentEvent(event.getEventType())) {
            return identities.findActiveParents(event.getFamilyId());
        }
        return identities.findActiveChild(event.getChildId()).map(List::of).orElseGet(List::of);
    }

    private boolean isParentEvent(ApplicationOutboxEventType type) {
        return type == ApplicationOutboxEventType.TASK_REQUEST_CREATED
            || type == ApplicationOutboxEventType.REWARD_REQUEST_CREATED;
    }

    private String status(ApplicationOutboxEventEntity event) {
        List<TelegramDeliveryEntity> eventDeliveries = deliveries.findByEvent(event.getId());
        if (eventDeliveries.isEmpty()) {
            return "NO_RECIPIENTS";
        }
        return eventDeliveries.stream().allMatch(this::isTerminal) ? "COMPLETE" : "PLANNED";
    }

    private boolean isTerminal(TelegramDeliveryEntity delivery) {
        return "SENT".equals(delivery.getStatus()) || "SKIPPED".equals(delivery.getStatus())
            || "SKIPPED_DISABLED".equals(delivery.getStatus()) || "FAILED".equals(delivery.getStatus());
    }
}
