package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class TelegramChildOutcomeText {
  private final Supplier<PurchaseRequestRepository> requests;
  private final Supplier<ShopItemRepository> shopItems;

  @Inject
  public TelegramChildOutcomeText(
      PurchaseRequestRepository requests, ShopItemRepository shopItems) {
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
      case TASK_APPROVED ->
          TelegramOutcomeCopy.childTaskApproved(requestTitle, delta, balance == null ? 0 : balance);
      case REWARD_APPROVED -> TelegramOutcomeCopy.childRewardApproved(requestTitle);
      case TASK_REJECTED -> TelegramOutcomeCopy.childTaskRejected(requestTitle);
      case REWARD_REJECTED -> TelegramOutcomeCopy.childRewardRejected(requestTitle);
      default -> generic(event);
    };
  }

  private String parentActionText(ApplicationOutboxEventEntity event) {
    int delta = event.getCoinDelta();
    Integer balance = event.getResultingBalance();
    return switch (event.getEventType()) {
      case TASK_APPROVED ->
          TelegramParentActionCopy.taskCompleted(delta, balance == null ? 0 : balance);
      case REWARD_PURCHASED ->
          TelegramParentActionCopy.rewardGranted(delta, balance == null ? 0 : balance);
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
      return shopItems
          .get()
          .findByIdOptional(request.getItemId())
          .map(ShopItemEntity::getName)
          .orElse(null);
    }
    return null;
  }

  private String generic(ApplicationOutboxEventEntity event) {
    String action =
        switch (event.getEventType()) {
          case TASK_REQUEST_CREATED -> "telegram.notification.taskRequestGeneric";
          case REWARD_REQUEST_CREATED -> "telegram.notification.rewardRequestGeneric";
          case TASK_APPROVED -> "telegram.notification.taskApproved";
          case TASK_REJECTED -> "telegram.notification.taskRejected";
          case REWARD_PURCHASED -> "telegram.notification.rewardPurchased";
          case REWARD_APPROVED -> "telegram.notification.rewardApproved";
          case REWARD_REJECTED -> "telegram.notification.rewardRejected";
          case BALANCE_ADJUSTED -> "telegram.notification.balanceAdjusted";
          case REQUEST_RESOLVED -> "telegram.notification.requestResolved";
        };
    action = TelegramMessageResolverHolder.text(action);
    if (event.getResultingBalance() == null || event.getCoinDelta() == 0) {
      return action;
    }
    return action
        + "\n"
        + TelegramCoinCopy.delta(event.getCoinDelta(), event.getCoinDelta() > 0, true)
        + "\n"
        + TelegramMessageResolverHolder.text("telegram.notification.balance",
            java.util.Map.of("balance", event.getResultingBalance()));
  }
}
