package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.config.TelegramConfig;

import java.util.function.Supplier;

// EXPLAIN: Routes persistent reply keyboard button taps to navigation content.
// EXPLAIN: UX-01 — extracted from TelegramBotServiceImpl to keep the service
// EXPLAIN: focused on webhook dispatch (SRP) and below the PMD GodClass gate.
// EXPLAIN: The feature gate is checked by the caller before this runs.
public class TelegramReplyKeyboardNavigator {

    private final TelegramQuickActionService quickActions;
    private final TelegramMenuBuilder menuBuilder;
    private final TelegramConfig config;
    private final Supplier<TelegramBotApiClient> apiClient;

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient) {
        this.quickActions = quickActions;
        this.menuBuilder = menuBuilder;
        this.config = config;
        this.apiClient = () -> apiClient;
    }

    public void handle(JsonNode message, long chatId, long telegramUserId) throws Exception {
        String label = message.path("text").asText("");
        BotNavAction.fromLabel(label).ifPresent(action -> {
            try {
                // EXPLAIN: OPEN_SITE is a plain text button (KeyboardButton has
                // EXPLAIN: no `url` field), so its tap arrives here and is
                // EXPLAIN: answered with one inline URL button.
                if (action == BotNavAction.OPEN_SITE) {
                    sendSiteLink(chatId);
                } else {
                    navigateByAction(chatId, telegramUserId, action.actionCode());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // EXPLAIN: UX-04 — a reply keyboard button cannot open an arbitrary external
    // EXPLAIN: URL (only web_app Mini Apps). The site button therefore sends a
    // EXPLAIN: single compact message with the same heading and one URL button.
    private void sendSiteLink(long chatId) throws Exception {
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
        if (publicSiteUrl.isEmpty()) {
            return;
        }
        apiClient.get().sendMessage(chatId, TelegramCopy.NAV_OPEN_SITE,
            java.util.List.of(TelegramBotApiClient.InlineButton.url(TelegramCopy.NAV_OPEN_SITE, publicSiteUrl, null)));
    }

    // EXPLAIN: UX-01 — sends navigation content with the persistent reply keyboard.
    // EXPLAIN: UX-06 — the reply keyboard is always present; no inline menu is appended.
    private void navigateByAction(long chatId, long telegramUserId, String action) throws Exception {
        if (quickActions == null || menuBuilder == null) {
            return;
        }
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
        quickActions.load(telegramUserId, TelegramMenuFlow.selectedChildId(action))
            .ifPresent(view -> {
                try {
                    String navText = TelegramMenuFlow.navigationText(action, view);
                    TelegramReplyKeyboard replyKeyboard = "child".equals(view.role())
                        ? new BotKeyboardFactory(null).childMain()
                        : new BotKeyboardFactory(publicSiteUrl).parentMain();
                    apiClient.get().sendMessageWithReplyKeyboard(chatId, navText, replyKeyboard);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
    }
}
