package com.sashplatonov.earnit.kids.telegram.application.notification;

public record TelegramRetentionReport(
        int invitations,
        int callbacks,
        int webhookUpdates,
        int deliveries,
        int outboxEvents,
        int auditEvents) {
    public int total() {
        return invitations + callbacks + webhookUpdates + deliveries + outboxEvents + auditEvents;
    }
}
