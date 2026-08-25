package com.sashplatonov.earnit.kids.telegram.application.notification;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramCopy;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramOutcomeCopy;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramBotApiClient;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramCoinCopy;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramChildOutcomeText;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramBotEmoji;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramLocaleContext;

import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class TelegramNotificationComposer {
    private final Supplier<ChildRepository> children;
    private final Supplier<PurchaseRequestRepository> requests;
    private final Supplier<ShopItemRepository> shopItems;
    private final Supplier<TelegramCallbackService> callbacks;
    private final Supplier<TelegramChildOutcomeText> outcomeText;
    private final Supplier<FamilyRepository> families;

    @Inject
    public TelegramNotificationComposer(ChildRepository children,
                                        PurchaseRequestRepository requests,
                                        ShopItemRepository shopItems,
                                        TelegramCallbackService callbacks,
                                        TelegramChildOutcomeText outcomeText,
                                        FamilyRepository families) {
        this.children = () -> children;
        this.requests = () -> requests;
        this.shopItems = () -> shopItems;
        this.callbacks = callbacks == null ? null : () -> callbacks;
        this.outcomeText = () -> outcomeText;
        this.families = families == null ? null : () -> families;
    }

    TelegramNotificationComposer(ChildRepository children,
                                 PurchaseRequestRepository requests,
                                 ShopItemRepository shopItems,
                                 TelegramCallbackService callbacks) {
        this(children, requests, shopItems, callbacks,
            new TelegramChildOutcomeText(requests, shopItems), null);
    }

    public List<TelegramBotApiClient.InlineButton> buttons(ApplicationOutboxEventEntity event) {
        return withFamilyLocale(event, () -> isRequestEvent(event.getEventType())
            ? requestButtons(event) : childOutcomeButtons(event.getEventType()));
    }

    public String text(ApplicationOutboxEventEntity event) {
        return withFamilyLocale(event, () -> isRequestEvent(event.getEventType())
            ? requestText(event) : childOutcomeText(event));
    }

    public String resolvedText(ApplicationOutboxEventEntity event) {
        return withFamilyLocale(event, () -> TelegramOutcomeCopy.requestResolved(
            event.getResolutionTitle(), event.getResolutionStatus()));
    }

    @SuppressWarnings("unchecked")
    private <T> T withFamilyLocale(ApplicationOutboxEventEntity event, java.util.function.Supplier<T> action) {
        FamilyLocale locale = families == null ? FamilyLocale.ru : families.get().findByDbId(event.getFamilyId())
            .map(value -> value.getLocale() == null ? FamilyLocale.en : value.getLocale())
            .orElse(FamilyLocale.en);
        final Object[] result = new Object[1];
        try {
            TelegramLocaleContext.with(locale, () -> result[0] = action.get());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return (T) result[0];
    }

    private List<TelegramBotApiClient.InlineButton> requestButtons(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return List.of();
        }
        String target = event.getChildId() + "." + event.getRequestId();
        return List.of(
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.approve(com.sashplatonov.earnit.kids.telegram.application.bot.TelegramLocaleContext.current()), "parent.request.approve." + target),
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.reject(com.sashplatonov.earnit.kids.telegram.application.bot.TelegramLocaleContext.current()), "parent.request.reject." + target));
    }

    private List<TelegramBotApiClient.InlineButton> childOutcomeButtons(ApplicationOutboxEventType type) {
        if (callbacks == null) {
            return List.of();
        }
        return switch (type) {
            case TASK_APPROVED, REWARD_APPROVED ->
                List.of(nav(TelegramCopy.myTasks(TelegramLocaleContext.current()), "tasks"), nav(TelegramCopy.rewards(TelegramLocaleContext.current()), "rewards"));
            case TASK_REJECTED -> List.of(nav(TelegramCopy.myTasks(TelegramLocaleContext.current()), "tasks"));
            case REWARD_REJECTED -> List.of(nav(TelegramCopy.rewards(TelegramLocaleContext.current()), "rewards"));
            default -> List.of();
        };
    }

    private TelegramBotApiClient.InlineButton nav(String label, String action) {
        return TelegramBotApiClient.InlineButton.callback(label, callbacks.get().signNavigation(action));
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
        return outcomeText.get().text(event);
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
            return shopItems.get().findByIdOptional(request.getItemId())
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
        return action + "\n" + TelegramCoinCopy.delta(event.getCoinDelta(), event.getCoinDelta() > 0, true)
            + "\nBalance: " + event.getResultingBalance() + " " + TelegramBotEmoji.COINS;
    }
}
