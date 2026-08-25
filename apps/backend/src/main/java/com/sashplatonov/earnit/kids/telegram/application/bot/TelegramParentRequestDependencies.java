package com.sashplatonov.earnit.kids.telegram.application.bot;

record TelegramParentRequestDependencies(
    TelegramQuickActionService quickActions,
    TelegramBotApiClient apiClient,
    TelegramMenuBuilder menuBuilder) {}
