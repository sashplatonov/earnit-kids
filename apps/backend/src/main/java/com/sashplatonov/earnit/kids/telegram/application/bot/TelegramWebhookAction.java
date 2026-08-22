package com.sashplatonov.earnit.kids.telegram.application.bot;

@FunctionalInterface
interface TelegramWebhookAction {
    void run() throws Exception;
}
