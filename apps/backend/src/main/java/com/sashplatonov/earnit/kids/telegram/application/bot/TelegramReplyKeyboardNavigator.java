package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureSupport;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

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
                if (action == BotNavAction.OPEN_SITE) {
                    sendSiteLink(chatId, telegramUserId);
                } else {
                    navigateByAction(chatId, telegramUserId, action.actionCode());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void sendSiteLink(long chatId, long telegramUserId) throws Exception {
        String configuredUrl = config.publicSiteUrl().orElse("").trim();
        if (!isHttpUrl(configuredUrl)) {
            return;
        }
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(configuredUrl);
        if (publicSiteUrl.isEmpty()) {
            return;
        }
        FamilyLocale locale = quickActions == null
            ? FamilyLocale.en
            : quickActions.load(telegramUserId, null).map(view -> view.locale()).orElse(FamilyLocale.en);
        TelegramLocaleContext.with(locale, () -> {
            String siteLabel = TelegramCopy.site(locale);
            apiClient.get().sendMessage(chatId, siteLabel,
                java.util.List.of(TelegramBotApiClient.InlineButton.url(siteLabel, publicSiteUrl, null)));
        });
    }

    private boolean isHttpUrl(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void navigateByAction(long chatId, long telegramUserId, String action) throws Exception {
        if (quickActions == null || menuBuilder == null) {
            return;
        }
        String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
            quickActions.load(telegramUserId, TelegramMenuFlow.selectedChildId(action))
            .ifPresent(view -> {
                try {
                    TelegramLocaleContext.with(view.locale(), () -> {
                        String navText = TelegramMenuFlow.navigationText(action, view);
                        if ("parent".equals(view.role())) {
                            List<TelegramBotApiClient.InlineButton> inlineButtons =
                                TelegramMenuFlow.navigationMenu(action, view, config.miniAppUrl().orElse(""),
                                    publicSiteUrl, menuBuilder);
                            apiClient.get().sendMessage(chatId, navText, inlineButtons);
                        } else {
                            TelegramReplyKeyboard replyKeyboard = new BotKeyboardFactory(null).childMain();
                            apiClient.get().sendMessageWithReplyKeyboard(chatId, navText, replyKeyboard);
                        }
                    });
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
    }
}
