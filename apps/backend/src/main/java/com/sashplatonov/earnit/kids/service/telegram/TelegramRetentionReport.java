package com.sashplatonov.earnit.kids.service.telegram;

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
