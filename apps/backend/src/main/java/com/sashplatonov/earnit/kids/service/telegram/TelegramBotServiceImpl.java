package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
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

    private TelegramReplyKeyboardNavigator replyKeyboardNavigator() {
        return new TelegramReplyKeyboardNavigator(quickActions, menuBuilder, config, apiClient);
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
        if (chatId == Long.MIN_VALUE) {
            return;
        }
        if (!TelegramFeatureSupport.isEnabledForFamily(featureGate, identities, families, telegramUserId)) {
            return;
        }
        if (TelegramMenuFlow.isStartCommand(text)) {
            handleStartCommand(chatId, telegramUserId);
        } else if (telegramUserId != Long.MIN_VALUE) {
            replyKeyboardNavigator().handle(message, chatId, telegramUserId);
        }
    }

    private void handleStartCommand(long chatId, long telegramUserId) throws Exception {
        String miniAppUrl = config.miniAppUrl().orElse("");
        if (miniAppUrl.isBlank()) {
            return;
        }
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
        if (quickActions != null && menuBuilder != null && telegramUserId != Long.MIN_VALUE) {
            var view = quickActions.load(telegramUserId, null);
            if (view.isPresent()) {
                TelegramQuickActionResponse loaded = view.get();
                String homeText = TelegramMenuFlow.startText(loaded);
                BotKeyboardFactory kb = new BotKeyboardFactory(publicSiteUrl);
                TelegramReplyKeyboard replyKeyboard = "child".equals(loaded.role())
                    ? kb.childMain() : kb.parentMain();
                int keyboardVersion = config.replyKeyboardVersion();
                if (telegramUserId != Long.MIN_VALUE
                    && identities.needsReplyKeyboardReset(telegramUserId, keyboardVersion)) {
                    apiClient.removeReplyKeyboard(chatId);
                    identities.markReplyKeyboardVersion(telegramUserId, keyboardVersion);
                }
                apiClient.sendMessageWithReplyKeyboard(chatId, homeText, replyKeyboard);
            } else {
                var parent = identities.findActiveByTelegramUserId(telegramUserId)
                    .filter(identity -> "parent".equals(identity.role()));
                if (parent.isPresent()) {
                    apiClient.sendMessage(chatId, "No children yet",
                        menuBuilder.parentNoChildren(miniAppUrl));
                } else {
                    apiClient.sendMessage(chatId, START_TEXT,
                        List.of(new TelegramBotApiClient.InlineButton("Open Mini App", miniAppUrl)));
                }
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
            if (!TelegramFeatureSupport.isEnabledForFamily(featureGate, identities, families, telegramUserId)) {
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
            } else if (data.startsWith("task.request.") || data.startsWith("reward.request.")) {
                handleChildQuickAction(data, telegramUserId, callback);
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

    private void handleChildQuickAction(String data, long telegramUserId, JsonNode callback) {
        if (quickActions == null || menuBuilder == null) {
            return;
        }
        TelegramChildActionHandler childActions = new TelegramChildActionHandler(quickActions, apiClient, menuBuilder);
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
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
        quickActions.load(verified.telegramUserId(), TelegramMenuFlow.selectedChildId(verified.action()))
            .ifPresent(view -> {
            try {
                apiClient.editMessageText(chatId, messageId, TelegramMenuFlow.navigationText(verified.action(), view),
                    TelegramMenuFlow.navigationMenu(verified.action(), view, miniAppUrl, publicSiteUrl, menuBuilder));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

}
