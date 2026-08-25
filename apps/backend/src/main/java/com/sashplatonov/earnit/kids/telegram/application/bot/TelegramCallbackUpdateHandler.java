package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramActionIdParser;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureSupport;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import org.jboss.logging.Logger;

final class TelegramCallbackUpdateHandler {
  private static final Logger LOG = Logger.getLogger(TelegramCallbackUpdateHandler.class);

  private final TelegramIdentityService identities;
  private final TelegramBotApiClient apiClient;
  private final TelegramCallbackService callbacks;
  private final TelegramConfig config;
  private final TelegramQuickActionService quickActions;
  private final TelegramMenuBuilder menuBuilder;
  private final TelegramFeatureGate featureGate;
  private final FamilyRepository families;

  TelegramCallbackUpdateHandler(
      TelegramIdentityService identities,
      TelegramBotApiClient apiClient,
      TelegramCallbackService callbacks,
      TelegramConfig config,
      TelegramQuickActionService quickActions,
      TelegramMenuBuilder menuBuilder,
      TelegramFeatureGate featureGate,
      FamilyRepository families) {
    this.identities = identities;
    this.apiClient = apiClient;
    this.callbacks = callbacks;
    this.config = config;
    this.quickActions = quickActions;
    this.menuBuilder = menuBuilder;
    this.featureGate = featureGate;
    this.families = families;
  }

  void handle(JsonNode callback) throws Exception {
    String callbackId = callback.path("id").asText("");
    long telegramUserId = callback.path("from").path("id").asLong(Long.MIN_VALUE);
    String data = callback.path("data").asText("");
    if (callbackId.isBlank() || telegramUserId == Long.MIN_VALUE) {
      return;
    }
    acknowledge(callbackId);
    if (!TelegramFeatureSupport.isEnabledForFamily(
        featureGate, identities, families, telegramUserId)) {
      return;
    }
    if (data.startsWith("nav.")) {
      callbacks
          .verifyNavigation(data, telegramUserId)
          .ifPresent(
              verified -> {
                if (verified.action().startsWith("coins-apply-")) {
                  TelegramCoinAdjustmentHandler.handle(
                      telegramUserId,
                      verified.action(),
                      callback,
                      quickActions,
                      apiClient,
                      menuBuilder,
                      config.miniAppUrl().orElse(""));
                } else {
                  navigate(callback, verified);
                }
              });
    } else if (data.startsWith("task.request.") || data.startsWith("reward.request.")) {
      handleChildQuickAction(data, telegramUserId, callback);
    } else if (data.startsWith("parent.request.")) {
      TelegramParentRequestHandler.handle(
          telegramUserId, data, callback, quickActions, apiClient, menuBuilder);
    } else if (data.startsWith("mutate.")) {
      callbacks.consumeMutation(data.substring("mutate.".length()), telegramUserId);
    }
  }

  private void acknowledge(String callbackId) {
    try {
      apiClient.answerCallbackQuery(callbackId);
    } catch (Exception exception) {
      LOG.warn("Telegram callback acknowledgement failed", exception);
    }
  }

  private void handleChildQuickAction(String data, long telegramUserId, JsonNode callback) {
    if (quickActions == null || menuBuilder == null) {
      return;
    }
    TelegramChildActionHandler childActions =
        new TelegramChildActionHandler(quickActions, apiClient, menuBuilder);
    if (data.startsWith("task.request.")) {
      TelegramActionIdParser.parse(data, "task.request.")
          .ifPresent(taskId -> childActions.task(telegramUserId, taskId, callback));
    } else {
      TelegramActionIdParser.parse(data, "reward.request.")
          .ifPresent(rewardId -> childActions.reward(telegramUserId, rewardId, callback));
    }
  }

  private void navigate(JsonNode callback, TelegramCallbackService.VerifiedCallback verified) {
    if (quickActions == null || menuBuilder == null) {
      return;
    }
    long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
    long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
    if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
      return;
    }
    String miniAppUrl = config.miniAppUrl().orElse("");
    String publicSiteUrl =
        TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
    quickActions
        .load(verified.telegramUserId(), TelegramMenuFlow.selectedChildId(verified.action()))
        .ifPresent(
            view -> {
              try {
                TelegramLocaleContext.with(view.locale(), () -> {
                  apiClient.editMessageText(
                      chatId,
                      messageId,
                      TelegramMenuFlow.navigationText(verified.action(), view),
                      TelegramMenuFlow.navigationMenu(
                          verified.action(), view, miniAppUrl, publicSiteUrl, menuBuilder));
                });
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            });
  }
}
