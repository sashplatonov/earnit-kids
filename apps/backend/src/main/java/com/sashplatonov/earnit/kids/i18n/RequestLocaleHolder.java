package com.sashplatonov.earnit.kids.i18n;

public final class RequestLocaleHolder {
    private static final ThreadLocal<String> CURRENT_LOCALE = ThreadLocal.withInitial(() -> "en");

    private RequestLocaleHolder() {
    }

    public static String get() {
        String locale = CURRENT_LOCALE.get();
        return "ru".equals(locale) ? "ru" : "en";
    }

    public static void set(String locale) {
        CURRENT_LOCALE.set("ru".equalsIgnoreCase(locale) ? "ru" : "en");
    }

    public static void clear() {
        CURRENT_LOCALE.remove();
    }
}
