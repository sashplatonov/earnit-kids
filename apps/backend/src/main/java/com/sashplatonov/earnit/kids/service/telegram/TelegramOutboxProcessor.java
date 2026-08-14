package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
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
    @Inject private TelegramBotApiClient api;
    @Inject private TelegramConfig config;
    @Inject private TelegramObservability observability;
    @Inject private ChildRepository children;
    @Inject private PurchaseRequestRepository requests;
    @Inject private ShopItemRepository shopItems;
    @Inject private TelegramCallbackService callbacks;

    TelegramOutboxProcessor() {
    }

    TelegramOutboxProcessor(TelegramDeliveryPlanner planner,
                            TelegramDeliveryRepository deliveries,
                            ApplicationOutboxEventRepository events,
                            TelegramBotApiClient api,
                            TelegramConfig config,
                            TelegramObservability observability,
                            ChildRepository children,
                            PurchaseRequestRepository requests,
                            ShopItemRepository shopItems,
                            TelegramCallbackService callbacks) {
        this.planner = planner;
        this.deliveries = deliveries;
        this.events = events;
        this.api = api;
        this.config = config;
        this.observability = observability;
        this.children = children;
        this.requests = requests;
        this.shopItems = shopItems;
        this.callbacks = callbacks;
    }

    public TelegramOutboxProcessor(TelegramDeliveryPlanner planner,
                                   TelegramDeliveryRepository deliveries,
                                   ApplicationOutboxEventRepository events,
                                   TelegramBotApiClient api,
                                   TelegramConfig config,
                                   ChildRepository children,
                                   PurchaseRequestRepository requests,
                                   ShopItemRepository shopItems,
                                   TelegramCallbackService callbacks) {
        this(planner, deliveries, events, api, config, null, children, requests, shopItems, callbacks);
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
                    notificationText(event), notificationButtons(event)));
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

    // EXPLAIN: Request-created notifications carry one-callback approve/reject
    // EXPLAIN: buttons so a parent can decide directly from the notification;
    // EXPLAIN: child outcome notifications carry role navigation controls.
    private List<TelegramBotApiClient.InlineButton> notificationButtons(ApplicationOutboxEventEntity event) {
        if (isRequestEvent(event.getEventType())) {
            if (event.getRequestId() == null) {
                return List.of();
            }
            String target = event.getChildId() + "." + event.getRequestId();
            return List.of(
                TelegramBotApiClient.InlineButton.callback(TelegramCopy.APPROVE, "parent.request.approve." + target),
                TelegramBotApiClient.InlineButton.callback(TelegramCopy.REJECT, "parent.request.reject." + target));
        }
        return childOutcomeButtons(event.getEventType());
    }

    // EXPLAIN: Child outcome feedback points to the next action without a menu.
    private List<TelegramBotApiClient.InlineButton> childOutcomeButtons(ApplicationOutboxEventType type) {
        if (callbacks == null) {
            return List.of();
        }
        return switch (type) {
            case TASK_APPROVED -> List.of(nav(TelegramCopy.MY_TASKS, "tasks"), nav(TelegramCopy.REWARDS, "rewards"));
            case REWARD_APPROVED -> List.of(nav(TelegramCopy.MY_TASKS, "tasks"), nav(TelegramCopy.REWARDS, "rewards"));
            case TASK_REJECTED -> List.of(nav(TelegramCopy.MY_TASKS, "tasks"), nav(TelegramCopy.HOME, "main"));
            case REWARD_REJECTED -> List.of(nav(TelegramCopy.REWARDS, "rewards"), nav(TelegramCopy.HOME, "main"));
            default -> List.of();
        };
    }

    private TelegramBotApiClient.InlineButton nav(String label, String action) {
        return TelegramBotApiClient.InlineButton.callback(label, callbacks.signNavigation(action));
    }

    private boolean isRequestEvent(ApplicationOutboxEventType type) {
        return type == ApplicationOutboxEventType.TASK_REQUEST_CREATED
            || type == ApplicationOutboxEventType.REWARD_REQUEST_CREATED;
    }

    // EXPLAIN: Rich Russian notification text: request-created events name the
    // EXPLAIN: child and the request; child outcome events explain the decision.
    private String notificationText(ApplicationOutboxEventEntity event) {
        ApplicationOutboxEventType type = event.getEventType();
        if (isRequestEvent(type)) {
            Optional<PurchaseRequestEntity> request = request(event);
            Optional<ChildEntity> child = child(event.getChildId());
            if (request.isEmpty() || child.isEmpty()) {
                return text(event);
            }
            boolean task = type == ApplicationOutboxEventType.TASK_REQUEST_CREATED;
            return TelegramCopy.requestNotification(child.get().getName(), title(request.get()),
                request.get().getCoins(), task);
        }
        return childOutcomeText(event);
    }

    private String childOutcomeText(ApplicationOutboxEventEntity event) {
        Optional<PurchaseRequestEntity> request = request(event);
        String title = request.map(this::title).orElse(null);
        if (title == null) {
            return text(event);
        }
        int delta = event.getCoinDelta();
        Integer balance = event.getResultingBalance();
        return switch (event.getEventType()) {
            case TASK_APPROVED -> TelegramCopy.childTaskApproved(title, delta,
                balance == null ? 0 : balance);
            case REWARD_APPROVED -> TelegramCopy.childRewardApproved(title);
            case TASK_REJECTED -> TelegramCopy.childTaskRejected(title);
            case REWARD_REJECTED -> TelegramCopy.childRewardRejected(title);
            default -> text(event);
        };
    }

    private Optional<PurchaseRequestEntity> request(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return Optional.empty();
        }
        return requests.findByIdOptional(event.getRequestId());
    }

    private Optional<ChildEntity> child(Integer childId) {
        if (childId == null) {
            return Optional.empty();
        }
        return children.findByIdOptional(childId);
    }

    private String title(PurchaseRequestEntity request) {
        if (request.getTaskName() != null) {
            return request.getTaskName();
        }
        if (request.getItemId() != null) {
            return shopItems.findByIdOptional(request.getItemId())
                .map(ShopItemEntity::getName)
                .orElse(null);
        }
        return null;
    }

    private String text(ApplicationOutboxEventEntity event) {
        String action = switch (event.getEventType()) {
            case TASK_REQUEST_CREATED -> "New task request";
            case REWARD_REQUEST_CREATED -> "New reward request";
            case TASK_APPROVED -> TelegramBotEmoji.DONE + " Task approved";
            case TASK_REJECTED -> TelegramBotEmoji.REJECT + " Task rejected";
            case REWARD_PURCHASED -> TelegramBotEmoji.REWARD + " Reward purchased";
            case REWARD_APPROVED -> TelegramBotEmoji.REWARD + " Reward approved";
            case REWARD_REJECTED -> TelegramBotEmoji.REJECT + " Reward rejected";
            case BALANCE_ADJUSTED -> TelegramBotEmoji.COINS + " Parent adjusted balance";
        };
        if (event.getResultingBalance() == null || event.getCoinDelta() == 0) {
            return action;
        }
        return action + "\n" + (event.getCoinDelta() > 0 ? "+" : "") + event.getCoinDelta()
            + " " + TelegramBotEmoji.COINS + "\nBalance: " + event.getResultingBalance() + " " + TelegramBotEmoji.COINS;
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
