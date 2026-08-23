package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TelegramBotServiceImpl implements TelegramBotService {
    private final TelegramIdentityService identities;
    private final TimeProvider timeProvider;
    private final TelegramMessageUpdateHandler messageHandler;
    private final TelegramCallbackUpdateHandler callbackHandler;

    public TelegramBotServiceImpl(TelegramIdentityService identities,
                                  TelegramBotApiClient apiClient,
                                  TelegramCallbackService callbacks,
                                  TelegramConfig config,
                                  TimeProvider timeProvider) {
        this(identities, apiClient, callbacks, config, timeProvider, null, null, null, null);
    }

    @Inject
    public TelegramBotServiceImpl(TelegramIdentityService identities,
                                  TelegramBotApiClient apiClient,
                                  TelegramCallbackService callbacks,
                                  TelegramConfig config,
                                  TimeProvider timeProvider,
                                  TelegramQuickActionService quickActions,
                                  TelegramMenuBuilder menuBuilder,
                                  TelegramFeatureGate featureGate,
                                  FamilyRepository families) {
        this.identities = identities;
        this.timeProvider = timeProvider;
        TelegramReplyKeyboardNavigator navigator =
            new TelegramReplyKeyboardNavigator(quickActions, menuBuilder, config, apiClient);
        this.messageHandler = new TelegramMessageUpdateHandler(identities, apiClient, config,
            quickActions, menuBuilder, featureGate, families, navigator);
        this.callbackHandler = new TelegramCallbackUpdateHandler(identities, apiClient, callbacks,
            config, quickActions, menuBuilder, featureGate, families);
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
            TelegramWebhookExecution.run("message", () -> messageHandler.handle(update.get("message")));
        } else if (update.has("callback_query")) {
            TelegramWebhookExecution.run("callback", () -> callbackHandler.handle(update.get("callback_query")));
        }
    }
}
