package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.TelegramDeliveryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;

import java.time.Instant;

@ApplicationScoped
public class TelegramOutboxProcessor {
    private static final long DELIVERY_CLAIM_LEASE_SECONDS = 60;
    @Inject private TelegramDeliveryPlanner planner;
    @Inject private TelegramDeliveryRepository deliveries;
    @Inject private ApplicationOutboxEventRepository events;
    @Inject private TelegramBotApiClient api;
    @Inject private TelegramConfig config;
    @Inject private TelegramObservability observability;
    @Inject private TelegramNotificationComposer composer;

    TelegramOutboxProcessor() {
    }

    TelegramOutboxProcessor(TelegramDeliveryPlanner planner,
                            TelegramDeliveryRepository deliveries,
                            ApplicationOutboxEventRepository events,
                            TelegramBotApiClient api,
                            TelegramConfig config,
                            TelegramObservability observability,
                            TelegramNotificationComposer composer) {
        this.planner = planner;
        this.deliveries = deliveries;
        this.events = events;
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
        this(planner, deliveries, events, api, config, null, composer);
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
                delivery.setMessageId(api.sendMessage(delivery.getChatId(),
                    composer.text(event), composer.buttons(event)));
                delivery.setStatus("SENT");
                if (observability != null) observability.outbox("sent");
                delivery.setSentAt(now);
                delivery.setTerminalAt(now);
                delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                markEventComplete(event, now);
                sent++;
            } catch (Exception failure) {
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
