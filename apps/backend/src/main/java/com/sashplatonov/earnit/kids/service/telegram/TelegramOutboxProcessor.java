package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus;
import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
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
    @Inject private TelegramDeliveryPlanner planner;
    @Inject private TelegramDeliveryRepository deliveries;
    @Inject private ApplicationOutboxEventRepository events;
    @Inject private PurchaseRequestRepository requests;
    @Inject private TelegramBotApiClient api;
    @Inject private TelegramConfig config;
    @Inject private TelegramObservability observability;
    @Inject private TelegramNotificationComposer composer;

    TelegramOutboxProcessor() {
    }

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
                if (failure instanceof TelegramApiException apiFailure && isNoOp(apiFailure)) {
                    // EXPLAIN: Message already absent or unchanged satisfies the
                    // EXPLAIN: invariant, so the delivery is terminal success.
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

    // EXPLAIN: REQUEST_RESOLVED edits the already-sent request message in place:
    // EXPLAIN: it shows the final status and drops the approve/reject buttons in
    // EXPLAIN: one Telegram mutation. No new message is sent. The message id was
    // EXPLAIN: copied from the original delivery at planning time.
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

    // EXPLAIN: A resolved message that is already gone (deleted by the user or
    // EXPLAIN: reported absent by Telegram) satisfies the invariant, so it is a
    // EXPLAIN: no-op success rather than a retryable failure.
    private boolean isNoOp(TelegramApiException failure) {
        if (failure.statusCode() != 400) {
            return false;
        }
        String description = failure.description() == null ? "" : failure.description().toLowerCase();
        return description.contains("message is not modified")
            || description.contains("message to edit not found")
            || description.contains("message to delete not found");
    }

    private boolean isRequestCreated(ApplicationOutboxEventType type) {
        return type == ApplicationOutboxEventType.TASK_REQUEST_CREATED
            || type == ApplicationOutboxEventType.REWARD_REQUEST_CREATED;
    }

    // EXPLAIN: Pre-send guard + post-send recheck close the late-send race: if
    // EXPLAIN: the request is already final before the initial send, no actionable
    // EXPLAIN: message is created; if it becomes final between the pre-check and
    // EXPLAIN: the send, the same final edit is applied right after persisting the
    // EXPLAIN: message id. A transient DB read failure is retried, never treated
    // EXPLAIN: as a deleted request.
    private void processRequestCreated(TelegramDeliveryEntity delivery, ApplicationOutboxEventEntity event, Instant now)
        throws Exception {
        Optional<PurchaseRequestEntity> request = requests.findByIdOptional(event.getRequestId());
        if (request.isEmpty() || isFinal(request.get().getStatus())) {
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
        if (recheck.isPresent() && isFinal(recheck.get().getStatus())) {
            api.editMessageText(delivery.getChatId(), delivery.getMessageId(),
                resolvedTextFor(recheck.get()), List.of());
            if (observability != null) observability.outbox("edited");
        }
        markEventComplete(event, now);
    }

    // EXPLAIN: Builds the final status text from a persisted request whose status
    // EXPLAIN: became final between the pre-send check and the send, so the edited
    // EXPLAIN: message reflects the actual resolution.
    private String resolvedTextFor(PurchaseRequestEntity request) {
        RequestResolutionStatus status = switch (request.getStatus()) {
            case approved -> RequestResolutionStatus.approved;
            case rejected -> RequestResolutionStatus.rejected;
            case cancelled -> RequestResolutionStatus.cancelled;
            case pending -> RequestResolutionStatus.deleted;
        };
        return TelegramCopy.requestResolved(request.getTaskName(), status);
    }

    private boolean isFinal(PurchaseRequestStatus status) {
        return status == PurchaseRequestStatus.approved
            || status == PurchaseRequestStatus.rejected
            || status == PurchaseRequestStatus.cancelled;
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
