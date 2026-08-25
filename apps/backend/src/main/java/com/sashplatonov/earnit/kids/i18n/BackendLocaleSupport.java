package com.sashplatonov.earnit.kids.i18n;

import java.util.List;
import java.util.Locale;

public final class BackendLocaleSupport {
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final List<Locale> SUPPORTED_LOCALES = List.of(ENGLISH, RUSSIAN);

    private BackendLocaleSupport() {
    }

    public static Locale defaultLocale() {
        return ENGLISH;
    }

    public static List<Locale> supportedLocales() {
        return SUPPORTED_LOCALES;
    }

    public static Locale resolveLocale(String appLocaleHeader, String acceptLanguageHeader) {
        Locale explicitLocale = normalizeLocale(appLocaleHeader);
        if (explicitLocale != null) {
            return explicitLocale;
        }

        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return defaultLocale();
        }

        try {
            Locale resolvedLocale = Locale.lookup(
                Locale.LanguageRange.parse(acceptLanguageHeader),
                supportedLocales()
            );
            return supportedOrDefault(resolvedLocale);
        } catch (IllegalArgumentException ex) {
            return supportedOrDefault(normalizeLocale(acceptLanguageHeader));
        }
    }

    public static Locale supportedOrDefault(Locale locale) {
        Locale normalizedLocale = normalizeLocale(locale);
        return normalizedLocale != null ? normalizedLocale : defaultLocale();
    }

    public static Locale normalizeLocale(Locale locale) {
        return locale == null ? null : normalizeLocale(locale.toLanguageTag());
    }

    public static Locale normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        if (normalizedValue.equals("ru") || normalizedValue.equals("ru-ru")) {
            return RUSSIAN;
        }
        if (normalizedValue.equals("en") || normalizedValue.equals("en-us")) {
            return ENGLISH;
        }
        return null;
    }

    public static String toLanguageTag(Locale locale) {
        return supportedOrDefault(locale).getLanguage();
    }
}
