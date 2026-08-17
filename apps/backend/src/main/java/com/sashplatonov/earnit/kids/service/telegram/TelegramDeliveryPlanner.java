package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class TelegramDeliveryPlanner {
    private static final Duration PLANNING_LEASE = Duration.ofMinutes(2);
    @Inject private ApplicationOutboxEventRepository events;
    @Inject private TelegramDeliveryRepository deliveries;
    @Inject private TelegramIdentityRepository identities;
    @Inject private FamilyRepository families;
    @Inject private TelegramFeatureGate featureGate;
    @Inject private TelegramObservability observability;

    TelegramDeliveryPlanner() {
    }

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
            event.setPlanningCompletedAt(now);
            event.setPlanningStatus(status(event));
        }
        return planned;
    }

    // EXPLAIN: REQUEST_RESOLVED is not a new notification: it targets the exact
    // EXPLAIN: request-created messages that were actually sent, so a delivery is
    // EXPLAIN: created per original sent message instead of per current parent.
    // EXPLAIN: The original message id is copied onto the resolved delivery so the
    // EXPLAIN: processor knows exactly which Telegram message to edit.
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
