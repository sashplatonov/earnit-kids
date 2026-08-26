package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureSupport;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

public class TelegramReplyKeyboardNavigator {

    private final TelegramQuickActionService quickActions;
    private final TelegramMenuBuilder menuBuilder;
    private final TelegramConfig config;
    private final Supplier<FamilyRepository> families;
    private final Supplier<TelegramBotApiClient> apiClient;

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient) {
        this(quickActions, menuBuilder, config, apiClient, null);
    }

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient,
                                          FamilyRepository families) {
        this.quickActions = quickActions;
        this.menuBuilder = menuBuilder;
        this.config = config;
        this.families = () -> families;
        this.apiClient = () -> apiClient;
    }

    public void handle(JsonNode message, long chatId, long telegramUserId) throws Exception {
        String label = message.path("text").asText("");
        BotNavAction.fromLabel(label).ifPresent(action -> {
            try {
                if (action == BotNavAction.OPEN_SITE) {
                    sendSiteLink(chatId, telegramUserId);
                } else if (action == BotNavAction.LANGUAGE) {
                    sendLanguagePicker(chatId, telegramUserId);
                } else {
                    navigateByAction(chatId, telegramUserId, action.actionCode());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        if (BotNavAction.fromLabel(label).isEmpty()) {
            handleLanguageChoice(label, chatId, telegramUserId);
        }
    }

    private void sendLanguagePicker(long chatId, long telegramUserId) throws Exception {
        if (quickActions == null) {
            return;
        }
        var view = quickActions.load(telegramUserId, null);
        if (view.isEmpty() || !"parent".equals(view.get().role())) {
            sendLanguageError(chatId, FamilyLocale.en);
            return;
        }
        FamilyLocale locale = view.get().locale() == null ? FamilyLocale.en : view.get().locale();
        TelegramLocaleContext.with(locale, () -> apiClient.get().sendMessageWithReplyKeyboard(
            chatId, TelegramCopy.languagePrompt(locale), new BotKeyboardFactory(null).languagePicker()));
    }

    private void handleLanguageChoice(String label, long chatId, long telegramUserId) throws Exception {
        FamilyLocale selected = languageChoice(label);
        if (selected == null || quickActions == null) {
            return;
        }
        var view = quickActions.load(telegramUserId, null);
        if (view.isEmpty() || !"parent".equals(view.get().role())
            || view.get().familyId() == null || view.get().familyId().isBlank() || families.get() == null) {
            sendLanguageError(chatId, view.map(value -> value.locale()).orElse(FamilyLocale.en));
            return;
        }
        FamilyLocale current = view.get().locale() == null ? FamilyLocale.en : view.get().locale();
        boolean updateSucceeded = selected == current;
        if (!updateSucceeded) {
            try {
                updateSucceeded = families.get().updateLocale(view.get().familyId(), selected);
            } catch (RuntimeException exception) {
                updateSucceeded = false;
            }
        }
        final boolean updated = updateSucceeded;
        FamilyLocale responseLocale = updated ? selected : current;
        TelegramLocaleContext.with(responseLocale, () -> {
            String response = updated
                ? (selected == current ? TelegramCopy.languageUnchanged(responseLocale) : TelegramCopy.languageUpdated(responseLocale))
                : TelegramCopy.languageError(responseLocale);
            if (updated) {
                String publicSiteUrl = TelegramFeatureSupport.normalizePublicSiteUrl(config.publicSiteUrl().orElse(""));
                apiClient.get().sendMessageWithReplyKeyboard(chatId, response,
                    new BotKeyboardFactory(publicSiteUrl).parentMain());
            } else {
                apiClient.get().sendMessage(chatId, response, List.of());
            }
        });
    }

    private FamilyLocale languageChoice(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        if (label.equals(TelegramCopy.languageEnglish(FamilyLocale.en))
            || label.equals(TelegramCopy.languageEnglish(FamilyLocale.ru))) {
            return FamilyLocale.en;
        }
        if (label.equals(TelegramCopy.languageRussian(FamilyLocale.en))
            || label.equals(TelegramCopy.languageRussian(FamilyLocale.ru))) {
            return FamilyLocale.ru;
        }
        return null;
    }

    private void sendLanguageError(long chatId, FamilyLocale locale) throws Exception {
        FamilyLocale safeLocale = locale == null ? FamilyLocale.en : locale;
        TelegramLocaleContext.with(safeLocale,
            () -> apiClient.get().sendMessage(chatId, TelegramCopy.languageError(safeLocale), List.of()));
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
            ? FamilyLocale.ru
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
