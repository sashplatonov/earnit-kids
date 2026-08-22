package com.sashplatonov.earnit.kids.telegram.application.bot;

final class TelegramWebhookExecution {
    private TelegramWebhookExecution() {
    }

    static void run(String updateType, TelegramWebhookAction action) {
        try {
            action.run();
        } catch (Exception exception) {
            throw new IllegalStateException("Telegram " + updateType + " processing failed", exception);
        }
    }
}
