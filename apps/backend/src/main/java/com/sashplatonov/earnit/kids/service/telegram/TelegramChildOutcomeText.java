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

import java.util.Optional;

// EXPLAIN: Renders the child-facing outcome notification text for approved,
// EXPLAIN: rejected, or parent-granted tasks/rewards. Kept out of the composer
// EXPLAIN: so the composer stays within the PMD GodClass guardrail (SRP).
@ApplicationScoped
public class TelegramChildOutcomeText {
    private final ChildRepository children;
    private final PurchaseRequestRepository requests;
    private final ShopItemRepository shopItems;

    @Inject
    public TelegramChildOutcomeText(ChildRepository children,
                                    PurchaseRequestRepository requests,
                                    ShopItemRepository shopItems) {
        this.children = children;
        this.requests = requests;
        this.shopItems = shopItems;
    }

    public String text(ApplicationOutboxEventEntity event) {
        // EXPLAIN: A direct parent action (task completed / reward granted) has
        // EXPLAIN: no child request, so requestId is null. Render the dedicated
        // EXPLAIN: Russian copy instead of the English generic fallback.
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
            case REWARD_PURCHASED -> TelegramParentActionCopy.rewardGranted();
            default -> generic(event);
        };
    }

    private Optional<PurchaseRequestEntity> request(ApplicationOutboxEventEntity event) {
        if (event.getRequestId() == null) {
            return Optional.empty();
        }
        return requests.findByIdOptional(event.getRequestId());
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
        };
        if (event.getResultingBalance() == null || event.getCoinDelta() == 0) {
            return action;
        }
        return action + "\n" + (event.getCoinDelta() > 0 ? "+" : "") + event.getCoinDelta()
            + " " + TelegramBotEmoji.COINS + "\nBalance: " + event.getResultingBalance() + " " + TelegramBotEmoji.COINS;
    }
}
