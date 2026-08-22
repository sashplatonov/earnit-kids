package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class TelegramChildOutcomeText {
    private final Supplier<PurchaseRequestRepository> requests;
    private final Supplier<ShopItemRepository> shopItems;

    @Inject
    public TelegramChildOutcomeText(PurchaseRequestRepository requests,
                                    ShopItemRepository shopItems) {
        this.requests = () -> requests;
        this.shopItems = () -> shopItems;
    }

    public String text(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return parentActionText(event);
        }
        String requestTitle = request(event).map(this::title).orElse(null);
        if (requestTitle == null) {
            return generic(event);
        }
        int delta = event.getCoinDelta();
        Integer balance = event.getResultingBalance();
        return switch (event.getEventType()) {
            case TASK_APPROVED -> TelegramCopy.childTaskApproved(requestTitle, delta,
                balance == null ? 0 : balance);
            case REWARD_APPROVED -> TelegramCopy.childRewardApproved(requestTitle);
            case TASK_REJECTED -> TelegramCopy.childTaskRejected(requestTitle);
            case REWARD_REJECTED -> TelegramCopy.childRewardRejected(requestTitle);
            default -> generic(event);
        };
    }

    private String parentActionText(ApplicationOutboxEventEntity event) {
        int delta = event.getCoinDelta();
        Integer balance = event.getResultingBalance();
        return switch (event.getEventType()) {
            case TASK_APPROVED -> TelegramParentActionCopy.taskCompleted(delta,
                balance == null ? 0 : balance);
            case REWARD_PURCHASED -> TelegramParentActionCopy.rewardGranted(delta,
                balance == null ? 0 : balance);
            default -> generic(event);
        };
    }

    private Optional<PurchaseRequestEntity> request(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return Optional.empty();
        }
        return requests.get().findByIdOptional(event.getRequestId());
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
