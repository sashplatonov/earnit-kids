package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

// EXPLAIN: Composes Telegram notification copy and buttons for outbox events so
// EXPLAIN: the delivery processor stays focused on transport (SRP guardrail).
@ApplicationScoped
public class TelegramNotificationComposer {
    private final Supplier<ChildRepository> children;
    private final Supplier<PurchaseRequestRepository> requests;
    private final ShopItemRepository shopItems;
    private final TelegramCallbackService callbacks;
    private final TelegramChildOutcomeText outcomeText;

    @Inject
    public TelegramNotificationComposer(ChildRepository children,
                                        PurchaseRequestRepository requests,
                                        ShopItemRepository shopItems,
                                        TelegramCallbackService callbacks,
                                        TelegramChildOutcomeText outcomeText) {
        this.children = () -> children;
        this.requests = () -> requests;
        this.shopItems = shopItems;
        this.callbacks = callbacks;
        this.outcomeText = outcomeText;
    }

    // EXPLAIN: Test-only constructor that builds the child-outcome text helper
    // EXPLAIN: from the same repositories to keep existing unit tests intact.
    TelegramNotificationComposer(ChildRepository children,
                                 PurchaseRequestRepository requests,
                                 ShopItemRepository shopItems,
                                 TelegramCallbackService callbacks) {
        this(children, requests, shopItems, callbacks,
            new TelegramChildOutcomeText(requests, shopItems));
    }

    public List<TelegramBotApiClient.InlineButton> buttons(ApplicationOutboxEventEntity event) {
        if (isRequestEvent(event.getEventType())) {
            return requestButtons(event);
        }
        return childOutcomeButtons(event.getEventType());
    }

    public String text(ApplicationOutboxEventEntity event) {
        if (isRequestEvent(event.getEventType())) {
            return requestText(event);
        }
        return childOutcomeText(event);
    }

    // EXPLAIN: Final status text for a REQUEST_RESOLVED event. The title comes
    // EXPLAIN: from the event snapshot (captured before a physical delete) so the
    // EXPLAIN: message can be updated even after the request entity is gone.
    public String resolvedText(ApplicationOutboxEventEntity event) {
        return TelegramCopy.requestResolved(event.getResolutionTitle(), event.getResolutionStatus());
    }

    // EXPLAIN: Request-created notifications carry one-callback approve/reject
    // EXPLAIN: buttons so a parent can decide directly from the notification.
    private List<TelegramBotApiClient.InlineButton> requestButtons(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return List.of();
        }
        String target = event.getChildId() + "." + event.getRequestId();
        return List.of(
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.APPROVE, "parent.request.approve." + target),
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.REJECT, "parent.request.reject." + target));
    }

    // EXPLAIN: Child outcome feedback points to the next action without a menu.
    private List<TelegramBotApiClient.InlineButton> childOutcomeButtons(ApplicationOutboxEventType type) {
        if (callbacks == null) {
            return List.of();
        }
        return switch (type) {
            case TASK_APPROVED, REWARD_APPROVED ->
                List.of(nav(TelegramCopy.MY_TASKS, "tasks"), nav(TelegramCopy.REWARDS, "rewards"));
            case TASK_REJECTED -> List.of(nav(TelegramCopy.MY_TASKS, "tasks"), nav(TelegramCopy.HOME, "main"));
            case REWARD_REJECTED -> List.of(nav(TelegramCopy.REWARDS, "rewards"), nav(TelegramCopy.HOME, "main"));
            default -> List.of();
        };
    }

    private TelegramBotApiClient.InlineButton nav(String label, String action) {
        return TelegramBotApiClient.InlineButton.callback(label, callbacks.signNavigation(action));
    }

    private String requestText(ApplicationOutboxEventEntity event) {
        Optional<PurchaseRequestEntity> request = request(event);
        Optional<ChildEntity> child = child(event.getChildId());
        if (request.isEmpty() || child.isEmpty()) {
            return generic(event);
        }
        boolean task = event.getEventType() == ApplicationOutboxEventType.TASK_REQUEST_CREATED;
        return TelegramCopy.requestNotification(child.get().getName(), title(request.get()),
            request.get().getCoins(), task);
    }

    private String childOutcomeText(ApplicationOutboxEventEntity event) {
        return outcomeText.text(event);
    }

    private boolean isRequestEvent(ApplicationOutboxEventType type) {
        return type == ApplicationOutboxEventType.TASK_REQUEST_CREATED
            || type == ApplicationOutboxEventType.REWARD_REQUEST_CREATED;
    }

    private Optional<PurchaseRequestEntity> request(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return Optional.empty();
        }
        return requests.get().findByIdOptional(event.getRequestId());
    }

    private Optional<ChildEntity> child(Integer childId) {
        if (childId == null) {
            return Optional.empty();
        }
        return children.get().findByIdOptional(childId);
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

    private String generic(ApplicationOutboxEventEntity event) {
        String action = switch (event.getEventType()) {
            case TASK_REQUEST_CREATED -> "New task request";
            case REWARD_REQUEST_CREATED -> "New reward request";
            case TASK_APPROVED -> TelegramBotEmoji.DONE + " Task approved";
            case TASK_REJECTED -> TelegramBotEmoji.REJECT + " Task rejected";
            case REWARD_PURCHASED -> TelegramBotEmoji.REWARD + " Reward purchased";
            case REWARD_APPROVED -> TelegramBotEmoji.REWARD + " Reward approved";
            case REWARD_REJECTED -> TelegramBotEmoji.REJECT + " Reward rejected";
            case BALANCE_ADJUSTED -> TelegramBotEmoji.COINS + " Parent adjusted balance";
            case REQUEST_RESOLVED -> "Request resolved";
        };
        if (event.getResultingBalance() == null || event.getCoinDelta() == 0) {
            return action;
        }
        return action + "\n" + (event.getCoinDelta() > 0 ? "+" : "") + event.getCoinDelta()
            + " " + TelegramBotEmoji.COINS + "\nBalance: " + event.getResultingBalance() + " " + TelegramBotEmoji.COINS;
    }
}
