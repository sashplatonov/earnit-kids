package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: UX-05 — raw string comparisons must not be scattered across handlers.
public enum BotNavAction {
    REQUESTS("requests", TelegramCopy.NAV_REQUESTS),
    COINS("coins", TelegramCopy.NAV_COINS),
    RECENT("recent", TelegramCopy.NAV_RECENT),
    SELECT_CHILD("switch", TelegramCopy.NAV_SELECT_CHILD),
    OPEN_SITE("site", TelegramCopy.NAV_OPEN_SITE);

    private final String actionCode;
    private final String label;

    BotNavAction(String actionCode, String label) {
        this.actionCode = actionCode;
        this.label = label;
    }

    public String actionCode() {
        return actionCode;
    }

    public String label() {
        return label;
    }

    public static java.util.Optional<BotNavAction> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return java.util.Optional.empty();
        }
        for (BotNavAction nav : values()) {
            if (nav.label.equals(label)) {
                return java.util.Optional.of(nav);
            }
        }
        return java.util.Optional.empty();
    }
}
