package com.sashplatonov.earnit.kids.telegram.application.bot;

public final class TelegramApiException extends IllegalStateException {
    private final int statusCode;
    private final int retryAfterSeconds;
    private final String description;

    public TelegramApiException(int statusCode, String description, int retryAfterSeconds) {
        super("Telegram API request failed: " + statusCode + " - " + description);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.description = description;
    }

    public int statusCode() {
        return statusCode;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String description() {
        return description;
    }

    public boolean isNoOp() {
        if (statusCode != 400) {
            return false;
        }
        String text = description == null ? "" : description.toLowerCase();
        return text.contains("message is not modified")
            || text.contains("message to edit not found");
    }
}
