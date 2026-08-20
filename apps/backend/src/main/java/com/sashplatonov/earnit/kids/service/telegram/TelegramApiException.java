package com.sashplatonov.earnit.kids.service.telegram;

final class TelegramApiException extends IllegalStateException {
    private final int statusCode;
    private final int retryAfterSeconds;
    private final String description;

    TelegramApiException(int statusCode, String description, int retryAfterSeconds) {
        super("Telegram API request failed: " + statusCode + " - " + description);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.description = description;
    }

    int statusCode() {
        return statusCode;
    }

    int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    String description() {
        return description;
    }

    boolean isNoOp() {
        if (statusCode != 400) {
            return false;
        }
        String text = description == null ? "" : description.toLowerCase();
        return text.contains("message is not modified")
            || text.contains("message to edit not found");
    }
}
