package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class TelegramBotServiceImpl implements TelegramBotService {
    private static final String START_TEXT = "Open EarnIt Kids to continue.";
    private static final Logger LOG = Logger.getLogger(TelegramBotServiceImpl.class);
    @Inject private TelegramIdentityService identities;
    @Inject private TelegramBotApiClient apiClient;
    @Inject private TelegramCallbackService callbacks;
    @Inject private TelegramConfig config;
    @Inject private TimeProvider timeProvider;
    @Inject private TelegramQuickActionService quickActions;
    @Inject private TelegramMenuBuilder menuBuilder;
    @Inject private TelegramFeatureGate featureGate;
    @Inject private FamilyRepository families;

    public TelegramBotServiceImpl(TelegramIdentityService identities,
                                  TelegramBotApiClient apiClient,
                                  TelegramCallbackService callbacks,
                                  TelegramConfig config,
                                  TimeProvider timeProvider) {
        this(identities, apiClient, callbacks, config, timeProvider, null, null, null, null);
    }

    public TelegramBotServiceImpl(TelegramIdentityService identities,
                                  TelegramBotApiClient apiClient,
                                  TelegramCallbackService callbacks,
                                  TelegramConfig config,
                                  TimeProvider timeProvider,
                                  TelegramQuickActionService quickActions,
                                  TelegramMenuBuilder menuBuilder) {
        this(identities, apiClient, callbacks, config, timeProvider, quickActions, menuBuilder, null, null);
    }

    TelegramBotServiceImpl() {
    }

    TelegramBotServiceImpl(TelegramIdentityService identities,
                           TelegramBotApiClient apiClient,
                           TelegramCallbackService callbacks,
                           TelegramConfig config,
                           TimeProvider timeProvider,
                           TelegramQuickActionService quickActions,
                           TelegramMenuBuilder menuBuilder,
                           TelegramFeatureGate featureGate,
                           FamilyRepository families) {
        this.identities = identities;
        this.apiClient = apiClient;
        this.callbacks = callbacks;
        this.config = config;
        this.timeProvider = timeProvider;
        this.quickActions = quickActions;
        this.menuBuilder = menuBuilder;
        this.featureGate = featureGate;
        this.families = families;
    }

    @Override
    @Transactional
    public void handleUpdate(JsonNode update) {
        if (update == null || !update.has("update_id") || !update.get("update_id").canConvertToLong()) {
            return;
        }
        long updateId = update.get("update_id").longValue();
        if (!identities.recordWebhookUpdate(updateId, timeProvider.now())) {
            return;
        }
        if (update.has("message")) {
            TelegramWebhookExecution.run("message", () -> handleMessage(update.get("message")));
        } else if (update.has("callback_query")) {
            TelegramWebhookExecution.run("callback", () -> handleCallback(update.get("callback_query")));
        }
    }

    private void handleMessage(JsonNode message) throws Exception {
        String text = message.path("text").asText("");
        long chatId = message.path("chat").path("id").asLong(Long.MIN_VALUE);
        long telegramUserId = message.path("from").path("id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || !TelegramMenuFlow.isStartCommand(text)) {
            return;
        }
        if (!isEnabledForFamily(telegramUserId)) {
            return;
        }
        String miniAppUrl = config.miniAppUrl().orElse("");
        if (miniAppUrl.isBlank()) {
            return;
        }
        if (quickActions != null && menuBuilder != null && telegramUserId != Long.MIN_VALUE) {
            var view = quickActions.load(telegramUserId, null);
            if (view.isPresent()) {
                try {
                    TelegramQuickActionResponse loaded = view.get();
                    apiClient.sendMessage(chatId, TelegramMenuFlow.startText(loaded),
                        TelegramMenuFlow.startMenu(loaded, miniAppUrl, menuBuilder));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            } else {
                var parent = identities.findActiveByTelegramUserId(telegramUserId)
                    .filter(identity -> "parent".equals(identity.role()));
                apiClient.sendMessage(chatId, parent.map(ignored -> "No children yet").orElse(START_TEXT),
                    parent.map(ignored -> menuBuilder.parentNoChildren(miniAppUrl))
                        .orElseGet(() -> List.of(new TelegramBotApiClient.InlineButton("Open Mini App", miniAppUrl))));
            }
            return;
        }
        apiClient.sendMessage(chatId, START_TEXT,
            List.of(new TelegramBotApiClient.InlineButton("Open Mini App", miniAppUrl)));
    }

    private void handleCallback(JsonNode callback) throws Exception {
        String callbackId = callback.path("id").asText("");
        long telegramUserId = callback.path("from").path("id").asLong(Long.MIN_VALUE);
        String data = callback.path("data").asText("");
        if (callbackId.isBlank() || telegramUserId == Long.MIN_VALUE) {
            return;
        }
        try {
            if (!isEnabledForFamily(telegramUserId)) {
                return;
            }
            if (data.startsWith("nav.")) {
                callbacks.verifyNavigation(data, telegramUserId)
                    .ifPresent(verified -> {
                        if (verified.action().startsWith("coins-apply-")) {
                            TelegramCoinAdjustmentHandler.handle(telegramUserId, verified.action(), callback,
                                quickActions, apiClient, menuBuilder, config.miniAppUrl().orElse(""));
                        } else {
                            navigate(callback, verified);
                        }
                    });
            } else if (data.startsWith("task.request.")) {
                TelegramActionIdParser.parse(data, "task.request.")
                    .ifPresent(taskId -> quickActionTask(telegramUserId, taskId, callback));
            } else if (data.startsWith("reward.request.")) {
                TelegramActionIdParser.parse(data, "reward.request.")
                    .ifPresent(rewardId -> quickActionReward(telegramUserId, rewardId, callback));
            } else if (data.startsWith("parent.request.")) {
                TelegramParentRequestHandler.handle(telegramUserId, data, callback,
                    quickActions, apiClient, menuBuilder, config.miniAppUrl().orElse(""));
            } else if (data.startsWith("mutate.")) {
                callbacks.consumeMutation(data.substring("mutate.".length()), telegramUserId);
            }
        } finally {
            acknowledgeCallback(callbackId);
        }
    }

    private void acknowledgeCallback(String callbackId) {
        try {
            apiClient.answerCallbackQuery(callbackId);
        } catch (Exception exception) {
            LOG.warn("Telegram callback acknowledgement failed", exception);
        }
    }

    private void quickActionTask(long telegramUserId, long taskId, JsonNode callback) {
        if (quickActions == null || menuBuilder == null) {
            return;
        }
        quickActions.load(telegramUserId, null).ifPresent(view -> {
            String taskName = view.tasks().stream()
                .filter(task -> task.id() == taskId).map(task -> task.name()).findFirst().orElse(null);
            OperationResult<TelegramQuickActionResponse> result =
                quickActions.requestTask(telegramUserId, view.childId(), taskId);
            editTaskRequestResult(callback, result, taskName);
        });
    }

    private void quickActionReward(long telegramUserId, long rewardId, JsonNode callback) {
        if (quickActions == null || menuBuilder == null) {
            return;
        }
        quickActions.load(telegramUserId, null).ifPresent(view -> {
            OperationResult<TelegramQuickActionResponse> result =
                quickActions.requestReward(telegramUserId, view.childId(), rewardId);
            editRewardRequestResult(callback, result);
        });
    }

    private void editRewardRequestResult(JsonNode callback, OperationResult<TelegramQuickActionResponse> result) {
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        String text = result instanceof OperationResult.Success<TelegramQuickActionResponse>
            ? TelegramCopy.rewardWaiting() : TelegramCopy.error();
        try {
            apiClient.editMessageText(chatId, messageId, text, menuBuilder.backToMain());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void editTaskRequestResult(JsonNode callback,
                                       OperationResult<TelegramQuickActionResponse> result,
                                       String taskName) {
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        String text = result instanceof OperationResult.Success<TelegramQuickActionResponse>
            ? TelegramCopy.waiting(taskName == null ? "Задание" : taskName) : TelegramCopy.error();
        try {
            apiClient.editMessageText(chatId, messageId, text, menuBuilder.backToMain());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
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
        quickActions.load(verified.telegramUserId(), TelegramMenuFlow.selectedChildId(verified.action()))
            .ifPresent(view -> {
            try {
                apiClient.editMessageText(chatId, messageId, TelegramMenuFlow.navigationText(verified.action(), view),
                    TelegramMenuFlow.navigationMenu(verified.action(), view, miniAppUrl, menuBuilder));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private boolean isEnabledForFamily(long telegramUserId) {
        if (featureGate == null) {
            return true;
        }
        return identities.findActiveByTelegramUserId(telegramUserId)
            .flatMap(identity -> families.findFamilyIdByDbId(identity.familyId()))
            .map(featureGate::isBotEnabled)
            // EXPLAIN: Unlinked users receive generic /start entry without family data.
            .orElse(true);
    }

}
