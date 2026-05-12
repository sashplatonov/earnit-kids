package com.sashplatonov.earnit.kids.i18n;

import java.util.Locale;

public final class RequestLocaleHolder {
    private static final ThreadLocal<Locale> CURRENT_LOCALE = ThreadLocal.withInitial(BackendLocaleSupport::defaultLocale);

    private RequestLocaleHolder() {
    }

    public static String get() {
        return BackendLocaleSupport.toLanguageTag(CURRENT_LOCALE.get());
    }

    public static Locale getLocale() {
        return BackendLocaleSupport.supportedOrDefault(CURRENT_LOCALE.get());
    }

    public static void set(String locale) {
        set(BackendLocaleSupport.normalizeLocale(locale));
    }

    public static void set(Locale locale) {
        CURRENT_LOCALE.set(BackendLocaleSupport.supportedOrDefault(locale));
    }

    public static void clear() {
        CURRENT_LOCALE.remove();
    }
}

