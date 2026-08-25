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
        if (language.equals("en") || language.equals("en-us")) {
            return en;
        }
        if (language.equals("ru") || language.equals("ru-ru")) {
            return ru;
        }
        return null;
    }
}
