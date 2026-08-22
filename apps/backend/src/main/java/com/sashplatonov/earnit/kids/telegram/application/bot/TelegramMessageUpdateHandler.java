package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureSupport;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;

import java.util.List;

final class TelegramMessageUpdateHandler {
    private static final String START_TEXT = "Open EarnIt Kids to continue.";

    private final TelegramIdentityService identities;
    private final TelegramBotApiClient apiClient;
    private final TelegramConfig config;
    private final TelegramQuickActionService quickActions;
    private final TelegramMenuBuilder menuBuilder;
    private final TelegramFeatureGate featureGate;
    private final FamilyRepository families;
    private final TelegramReplyKeyboardNavigator replyKeyboardNavigator;

    TelegramMessageUpdateHandler(TelegramIdentityService identities,
                                 TelegramBotApiClient apiClient,
                                 TelegramConfig config,
                                 TelegramQuickActionService quickActions,
                                 TelegramMenuBuilder menuBuilder,
                                 TelegramFeatureGate featureGate,
                                 FamilyRepository families,
                                 TelegramReplyKeyboardNavigator replyKeyboardNavigator) {
        this.identities = identities;
        this.apiClient = apiClient;
        this.config = config;
        this.quickActions = quickActions;
        this.menuBuilder = menuBuilder;
        this.featureGate = featureGate;
        this.families = families;
        this.replyKeyboardNavigator = replyKeyboardNavigator;
    }

    void handle(JsonNode message) throws Exception {
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
            replyKeyboardNavigator.handle(message, chatId, telegramUserId);
        }
    }

    private void handleStartCommand(long chatId, long telegramUserId) throws Exception {
        String miniAppUrl = config.miniAppUrl().orElse("");
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
                if (identities.needsReplyKeyboardReset(telegramUserId, keyboardVersion)) {
                    apiClient.removeReplyKeyboard(chatId);
                    identities.markReplyKeyboardVersion(telegramUserId, keyboardVersion);
                }
                apiClient.sendMessageWithReplyKeyboard(chatId, homeText, replyKeyboard);
            } else {
                var parent = identities.findActiveByTelegramUserId(telegramUserId)
                    .filter(identity -> "parent".equals(identity.role()));
                if (parent.isPresent()) {
                    apiClient.sendMessage(chatId, "No children yet", menuBuilder.parentNoChildren(miniAppUrl));
                } else {
                    apiClient.sendMessage(chatId, START_TEXT, List.of());
                }
            }
            return;
        }
        apiClient.sendMessage(chatId, START_TEXT, List.of());
    }
}
