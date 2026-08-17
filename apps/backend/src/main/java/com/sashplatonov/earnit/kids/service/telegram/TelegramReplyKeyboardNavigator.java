package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.config.TelegramConfig;

// EXPLAIN: Routes persistent reply keyboard button taps to navigation content.
// EXPLAIN: UX-01 — extracted from TelegramBotServiceImpl to keep the service
// EXPLAIN: focused on webhook dispatch (SRP) and below the PMD GodClass gate.
// EXPLAIN: The feature gate is checked by the caller before this runs.
public class TelegramReplyKeyboardNavigator {

    private final TelegramQuickActionService quickActions;
    private final TelegramMenuBuilder menuBuilder;
    private final TelegramConfig config;
    private final TelegramBotApiClient apiClient;

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient) {
        this.quickActions = quickActions;
        this.menuBuilder = menuBuilder;
        this.config = config;
        this.apiClient = apiClient;
    }

    // EXPLAIN: UX-01 — routes reply keyboard button taps to the appropriate handler.
    // EXPLAIN: The message text matches a BotNavAction label to determine navigation.
    public void handle(JsonNode message, long chatId, long telegramUserId) throws Exception {
        String label = message.path("text").asText("");
        BotNavAction.fromLabel(label).ifPresent(action -> {
            try {
                // EXPLAIN: OPEN_APP and OPEN_SITE are url buttons — they open
                // EXPLAIN: client-side and never send text to the bot, so only
                // EXPLAIN: nav actions that produce content reach this branch.
                navigateByAction(chatId, telegramUserId, action.actionCode());
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
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
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
}
