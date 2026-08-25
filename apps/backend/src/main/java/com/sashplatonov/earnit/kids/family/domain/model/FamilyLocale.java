package com.sashplatonov.earnit.kids.family.domain.model;

import java.util.Locale;

public enum FamilyLocale {
    en,
    ru;

    public static FamilyLocale fromLanguageTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String language = value.trim().toLowerCase(Locale.ROOT);
        if (language.startsWith("en")) {
            return en;
        }
        if (language.startsWith("ru")) {
            return ru;
        }
        return null;
    }
}
