package com.sashplatonov.earnit.kids.service.telegram;

@FunctionalInterface
interface TelegramWebhookAction {
    void run() throws Exception;
}
