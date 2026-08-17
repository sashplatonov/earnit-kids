package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Centralized navigation action labels for the persistent reply keyboard.
// EXPLAIN: UX-05 — raw string comparisons must not be scattered across handlers.
public enum BotNavAction {
    REQUESTS("requests", TelegramCopy.NAV_REQUESTS),
    COINS("coins", TelegramCopy.NAV_COINS),
    RECENT("recent", TelegramCopy.NAV_RECENT),
    SELECT_CHILD("switch", TelegramCopy.NAV_SELECT_CHILD),
    OPEN_APP("app", TelegramCopy.NAV_OPEN_APP),
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

    // EXPLAIN: Returns the BotNavAction matching a reply keyboard button label.
    // EXPLAIN: UX-01 — used to route reply keyboard taps back to navigation.
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

    // EXPLAIN: Returns the BotNavAction matching the given raw action string,
    // EXPLAIN: or empty when the action is not a persistent-keyboard action.
    public static java.util.Optional<BotNavAction> fromActionCode(String action) {
        if (action == null || action.isBlank()) {
            return java.util.Optional.empty();
        }
        String base = baseAction(action);
        for (BotNavAction nav : values()) {
            if (nav.actionCode.equals(base)) {
                return java.util.Optional.of(nav);
            }
        }
        return java.util.Optional.empty();
    }

    private static String baseAction(String action) {
        int marker = action.indexOf("-child-");
        return marker >= 0 ? action.substring(0, marker) : action;
    }
}
