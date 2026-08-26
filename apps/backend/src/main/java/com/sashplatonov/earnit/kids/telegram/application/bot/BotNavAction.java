package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

public enum BotNavAction {
    REQUESTS("requests"),
    COINS("coins"),
    RECENT("recent"),
    SELECT_CHILD("switch"),
    LANGUAGE("language"),
    OPEN_SITE("site");

    private final String actionCode;

    BotNavAction(String actionCode) {
        this.actionCode = actionCode;
    }

    public String actionCode() {
        return actionCode;
    }

    public static java.util.Optional<BotNavAction> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return java.util.Optional.empty();
        }
        for (BotNavAction nav : values()) {
            if (nav.localizedLabel(FamilyLocale.en).equals(label)
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
            case LANGUAGE -> TelegramCopy.language(locale);
            case OPEN_SITE -> TelegramCopy.site(locale);
        };
    }
}
