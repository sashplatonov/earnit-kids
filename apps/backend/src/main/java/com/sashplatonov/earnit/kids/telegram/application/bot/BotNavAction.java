package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

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
            if (nav.label.equals(label) || nav.localizedLabel(FamilyLocale.en).equals(label)
                    || nav.localizedLabel(FamilyLocale.ru).equals(label)) {
                return java.util.Optional.of(nav);
            }
        }
        return java.util.Optional.empty();
    }

    private String localizedLabel(FamilyLocale locale) {
        return switch (this) {
            case REQUESTS -> TelegramCopy.requests(locale);
            case COINS -> TelegramCopy.coins(locale);
            case RECENT -> TelegramCopy.recent(locale);
            case SELECT_CHILD -> TelegramCopy.switchChild(locale);
            case OPEN_SITE -> TelegramCopy.site(locale);
        };
    }
}
