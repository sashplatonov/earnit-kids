package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureSupport;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;

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
    private final Supplier<FamilyParentMembershipRepository> memberships;
    private final Supplier<TelegramIdentityService> identities;
    private final Supplier<TelegramBotApiClient> apiClient;

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient) {
        this(quickActions, menuBuilder, config, apiClient, null, null, null);
    }

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient,
                                          FamilyRepository families) {
        this(quickActions, menuBuilder, config, apiClient, families, null, null);
    }

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient,
                                          FamilyRepository families,
                                          FamilyParentMembershipRepository memberships) {
        this(quickActions, menuBuilder, config, apiClient, families, memberships, null);
    }

    public TelegramReplyKeyboardNavigator(TelegramQuickActionService quickActions,
                                          TelegramMenuBuilder menuBuilder,
                                          TelegramConfig config,
                                          TelegramBotApiClient apiClient,
                                          FamilyRepository families,
                                          FamilyParentMembershipRepository memberships,
                                          TelegramIdentityService identities) {
        this.quickActions = quickActions;
        this.menuBuilder = menuBuilder;
        this.config = config;
        this.families = () -> families;
        this.memberships = () -> memberships;
        this.identities = () -> identities;
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
        if (view.isEmpty() || !"parent".equals(view.get().role()) || !canManageLanguage(telegramUserId)) {
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
        var identity = languageManager(telegramUserId);
        if (view.isEmpty() || !"parent".equals(view.get().role()) || identity.isEmpty()
            || families.get() == null) {
            sendLanguageError(chatId, view.map(value -> value.locale()).orElse(FamilyLocale.en));
            return;
        }
        FamilyLocale current = view.get().locale() == null ? FamilyLocale.en : view.get().locale();
        boolean updateSucceeded = selected == current;
        if (!updateSucceeded) {
            try {
                String familyId = identity.get().familyId() == null ? view.get().familyId()
                    : families.get().findFamilyIdByDbId(identity.get().familyId()).orElse(null);
                updateSucceeded = familyId != null && !familyId.isBlank()
                    && families.get().updateLocale(familyId, selected);
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
                    new BotKeyboardFactory(publicSiteUrl).parentMain(canManageLanguage(telegramUserId)));
            } else {
                apiClient.get().sendMessage(chatId, response, List.of());
            }
        });
    }

    boolean canManageLanguage(long telegramUserId) {
        return languageManager(telegramUserId).isPresent();
    }

    private java.util.Optional<TelegramIdentityService.TelegramIdentity> languageManager(long telegramUserId) {
        if (memberships.get() == null) {
            return identities.get() == null ? (quickActions == null ? java.util.Optional.empty()
                : quickActions.load(telegramUserId, null)
                    .filter(view -> "parent".equals(view.role()))
                    .map(view -> new TelegramIdentityService.TelegramIdentity(null, null, null, telegramUserId,
                        "parent", null))) : identities.get().findActiveByTelegramUserId(telegramUserId)
                        .filter(identity -> "parent".equals(identity.role()));
        }
        if (identities.get() == null) {
            return java.util.Optional.empty();
        }
        return identities.get().findActiveByTelegramUserId(telegramUserId)
            .filter(identity -> "parent".equals(identity.role()))
            .filter(identity -> identity.parentAccountId() != null && identity.familyId() != null)
            .filter(identity -> memberships.get().findByParentAndFamily(identity.parentAccountId(), identity.familyId())
                .map(membership -> membership.getPermission()
                    == com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity.Permission.family_admin)
                .orElse(false));
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
