package com.sashplatonov.earnit.kids.telegram.application.notification;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramBotApiClient;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramApiException;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramRequestResolutionText;

import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramDeliveryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TelegramOutboxProcessor {
    private static final long DELIVERY_CLAIM_LEASE_SECONDS = 60;
    private TelegramDeliveryPlanner planner;
    private TelegramDeliveryRepository deliveries;
    private ApplicationOutboxEventRepository events;
    private PurchaseRequestRepository requests;
    private TelegramBotApiClient api;
    private TelegramConfig config;
    private TelegramObservability observability;
    private TelegramNotificationComposer composer;

    TelegramOutboxProcessor() {
    }

    @Inject
    TelegramOutboxProcessor(TelegramDeliveryPlanner planner,
                            TelegramDeliveryRepository deliveries,
                            ApplicationOutboxEventRepository events,
                            PurchaseRequestRepository requests,
                            TelegramBotApiClient api,
                            TelegramConfig config,
                            TelegramObservability observability,
                            TelegramNotificationComposer composer) {
        this.planner = planner;
        this.deliveries = deliveries;
        this.events = events;
        this.requests = requests;
        this.api = api;
        this.config = config;
        this.observability = observability;
        this.composer = composer;
    }

    public TelegramOutboxProcessor(TelegramDeliveryPlanner planner,
                                   TelegramDeliveryRepository deliveries,
                                   ApplicationOutboxEventRepository events,
                                   TelegramBotApiClient api,
                                   TelegramConfig config,
                                   TelegramNotificationComposer composer) {
        this(planner, deliveries, events, null, api, config, null, composer);
    }

    @Scheduled(every = "{app.telegram.outbox-poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        if (config.outboxEnabled()) {
            process(Instant.now());
        }
    }

    @Transactional
    public int process(Instant now) {
        planner.planDueEvents(now);
        int sent = 0;
        Instant expiredClaimBefore = now.minusSeconds(DELIVERY_CLAIM_LEASE_SECONDS);
        for (TelegramDeliveryEntity delivery : deliveries.findDue(now, expiredClaimBefore)) {
            if ("SKIPPED_DISABLED".equals(delivery.getStatus())) continue;
            delivery.setClaimedAt(now);
            ApplicationOutboxEventEntity event = events.findById(delivery.getEventId());
            if (event == null) {
                terminal(delivery, "MISSING_EVENT", now);
                continue;
            }
            try {
                if (event.getEventType() == ApplicationOutboxEventType.REQUEST_RESOLVED) {
                    processResolved(delivery, event, now);
                } else if (isRequestCreated(event.getEventType())) {
                    processRequestCreated(delivery, event, now);
                } else {
                    delivery.setMessageId(api.sendMessage(delivery.getChatId(),
                        composer.text(event), composer.buttons(event)));
                    delivery.setStatus("SENT");
                    if (observability != null) observability.outbox("sent");
                    delivery.setSentAt(now);
                    delivery.setTerminalAt(now);
                    delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                    markEventComplete(event, now);
                }
                sent++;
            } catch (Exception failure) {
                if (failure instanceof TelegramApiException apiFailure && apiFailure.isNoOp()) {
                    delivery.setStatus("SENT");
                    if (observability != null) observability.outbox("noop");
                    delivery.setSentAt(now);
                    delivery.setTerminalAt(now);
                    delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                    markEventComplete(event, now);
                    sent++;
                    continue;
                }
                delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                delivery.setLastError(failure.getClass().getSimpleName());
                if (delivery.getAttemptCount() >= config.outboxMaxAttempts()) {
                    if (observability != null) observability.outbox("failed");
                    terminal(delivery, "FAILED", now);
                    markEventComplete(event, now);
                } else {
                    if (observability != null) observability.outbox("retried");
                    delivery.setClaimedAt(null);
                    delivery.setNextAttemptAt(now.plusSeconds(1L << Math.min(6, delivery.getAttemptCount())));
                }
            }
        }
        return sent;
    }

    private void terminal(TelegramDeliveryEntity delivery, String status, Instant now) {
        delivery.setStatus(status);
        delivery.setTerminalAt(now);
        delivery.setClaimedAt(null);
    }

    private void processResolved(TelegramDeliveryEntity delivery, ApplicationOutboxEventEntity event, Instant now)
        throws Exception {
        if (delivery.getMessageId() == null) {
            terminal(delivery, "SKIPPED", now);
            if (observability != null) observability.outbox("skipped");
            return;
        }
        api.editMessageText(delivery.getChatId(), delivery.getMessageId(),
            composer.resolvedText(event), List.of());
        delivery.setStatus("SENT");
        if (observability != null) observability.outbox("edited");
        delivery.setSentAt(now);
        delivery.setTerminalAt(now);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        markEventComplete(event, now);
    }

    private boolean isRequestCreated(ApplicationOutboxEventType type) {
        return type == ApplicationOutboxEventType.TASK_REQUEST_CREATED
            || type == ApplicationOutboxEventType.REWARD_REQUEST_CREATED;
    }

    private void processRequestCreated(TelegramDeliveryEntity delivery, ApplicationOutboxEventEntity event, Instant now)
        throws Exception {
        Optional<PurchaseRequestEntity> request = requests.findByIdOptional(event.getRequestId());
        if (request.isEmpty() || TelegramRequestResolutionText.isFinal(request.get().getStatus())) {
            terminal(delivery, "SKIPPED", now);
            if (observability != null) observability.outbox("skipped");
            return;
        }
        delivery.setMessageId(api.sendMessage(delivery.getChatId(),
            composer.text(event), composer.buttons(event)));
        delivery.setStatus("SENT");
        if (observability != null) observability.outbox("sent");
        delivery.setSentAt(now);
        delivery.setTerminalAt(now);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        Optional<PurchaseRequestEntity> recheck = requests.findByIdOptional(event.getRequestId());
        if (recheck.isPresent() && TelegramRequestResolutionText.isFinal(recheck.get().getStatus())) {
            api.editMessageText(delivery.getChatId(), delivery.getMessageId(),
                TelegramRequestResolutionText.resolvedTextFor(recheck.get()), List.of());
            if (observability != null) observability.outbox("edited");
        }
        markEventComplete(event, now);
    }

    private void markEventComplete(ApplicationOutboxEventEntity event, Instant now) {
        if (events.findById(event.getId()) != null
            && deliveries.findByEvent(event.getId()).stream().allMatch(this::isTerminal)) {
            event.setPlanningStatus("COMPLETE");
            event.setPlanningCompletedAt(now);
        }
    }

    private boolean isTerminal(TelegramDeliveryEntity delivery) {
        return "SENT".equals(delivery.getStatus()) || "SKIPPED".equals(delivery.getStatus())
            || "SKIPPED_DISABLED".equals(delivery.getStatus()) || "FAILED".equals(delivery.getStatus());
    }
}
