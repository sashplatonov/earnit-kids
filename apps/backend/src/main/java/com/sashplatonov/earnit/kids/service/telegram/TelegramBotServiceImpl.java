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

import java.net.URI;
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
        if (chatId == Long.MIN_VALUE) {
            return;
        }
        // EXPLAIN: UX-01 — persistent reply keyboard navigation. If the text matches
        // EXPLAIN: a known nav action, handle it as navigation; otherwise treat as /start.
        if (TelegramMenuFlow.isStartCommand(text)) {
            if (!isEnabledForFamily(telegramUserId)) {
                return;
            }
            handleStartCommand(chatId, telegramUserId);
        } else if (telegramUserId != Long.MIN_VALUE) {
            handleReplyKeyboardNavigation(message, chatId, telegramUserId);
        }
    }

    private void handleStartCommand(long chatId, long telegramUserId) throws Exception {
        String miniAppUrl = config.miniAppUrl().orElse("");
        if (miniAppUrl.isBlank()) {
            return;
        }
        String publicSiteUrl = normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
        if (quickActions != null && menuBuilder != null && telegramUserId != Long.MIN_VALUE) {
            var view = quickActions.load(telegramUserId, null);
            if (view.isPresent()) {
                TelegramQuickActionResponse loaded = view.get();
                String homeText = TelegramMenuFlow.startText(loaded);
                BotKeyboardFactory kb = new BotKeyboardFactory(publicSiteUrl, miniAppUrl);
                TelegramReplyKeyboard replyKeyboard = "child".equals(loaded.role())
                    ? kb.childMain() : kb.parentMain();
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

    // EXPLAIN: UX-01 — routes reply keyboard button taps to the appropriate handler.
    // EXPLAIN: The message text matches a BotNavAction label to determine navigation.
    private void handleReplyKeyboardNavigation(JsonNode message, long chatId, long telegramUserId)
        throws Exception {
        String label = message.path("text").asText("");
        BotNavAction.fromLabel(label).ifPresent(action -> {
            try {
                if (!isEnabledForFamily(telegramUserId)) {
                    return;
                }
                if (action == BotNavAction.OPEN_SITE) {
                    // EXPLAIN: UX-04 — a persistent reply-keyboard button cannot open an
                    // EXPLAIN: external URL directly, so the bot replies with one compact
                    // EXPLAIN: message carrying a single URL button (no menu rebuild).
                    String publicSiteUrl = normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
                    if (!publicSiteUrl.isBlank()) {
                        apiClient.sendMessage(chatId, TelegramCopy.NAV_OPEN_SITE,
                            List.of(TelegramBotApiClient.InlineButton.url(
                                TelegramCopy.SHARE_SITE, publicSiteUrl, null)));
                    }
                } else if (action != BotNavAction.OPEN_APP) {
                    // EXPLAIN: OPEN_APP is a web_app button and opens client-side, so it
                    // EXPLAIN: never arrives here as a text message. All other nav actions
                    // EXPLAIN: navigate the home card.
                    navigateByAction(chatId, telegramUserId, action.actionCode());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // EXPLAIN: UX-01 — sends navigation content with the persistent reply keyboard.
    // EXPLAIN: UX-06 — the reply keyboard is always present; no inline menu is appended.
    private void navigateByAction(long chatId, long telegramUserId, String action) throws Exception {
        if (quickActions == null || menuBuilder == null) {
            return;
        }
        String publicSiteUrl = normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
        String miniAppUrl = config.miniAppUrl().orElse("");
        quickActions.load(telegramUserId, TelegramMenuFlow.selectedChildId(action))
            .ifPresent(view -> {
                try {
                    String navText = TelegramMenuFlow.navigationText(action, view);
                    TelegramReplyKeyboard replyKeyboard = "child".equals(view.role())
                        ? new BotKeyboardFactory(null, miniAppUrl).childMain()
                        : new BotKeyboardFactory(publicSiteUrl, miniAppUrl).parentMain();
                    apiClient.sendMessageWithReplyKeyboard(chatId, navText, replyKeyboard);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
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
        String publicSiteUrl = normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
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

    // EXPLAIN: The public site share button must point at the site root, never
    // EXPLAIN: at a specific app page. APP_URL may carry a path/query (e.g.
    // EXPLAIN: /en/app/tasks); strip it down to the bare origin so the button
    // EXPLAIN: always opens the public marketing site.
    private static String normalizePublicSiteUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return trimmed;
            }
            String origin = uri.getScheme() + ":" + '/' + '/' + uri.getHost();
            if (uri.getPort() >= 0) {
                origin = origin + ":" + uri.getPort();
            }
            return origin;
        } catch (Exception exception) {
            return trimmed;
        }
    }

}
