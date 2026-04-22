package com.sashplatonov.earnit.kids.i18n;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ContextNotActiveException;

import java.util.Locale;
import java.util.Map;

public final class BackendMessages {
    private BackendMessages() {
    }

    public static String resolveLocale(String appLocaleHeader, String acceptLanguageHeader) {
        Locale locale = BackendLocaleSupport.resolveLocale(appLocaleHeader, acceptLanguageHeader);
        return BackendLocaleSupport.toLanguageTag(locale);
    }

    public static String currentLocale() {
        return BackendLocaleSupport.toLanguageTag(currentLocaleObject());
    }

    public static String message(String key) {
        return BackendMessageCatalog.message(currentLocaleObject(), key, Map.of());
    }

    public static String message(String key, Map<String, ?> variables) {
        return BackendMessageCatalog.message(currentLocaleObject(), key, variables);
    }

    public static String message(String locale, String key, Map<String, ?> variables) {
        return BackendMessageCatalog.message(BackendLocaleSupport.normalizeLocale(locale), key, variables);
    }

    public static String statusTitle(int status) {
        return BackendMessageCatalog.statusTitle(currentLocaleObject(), status);
    }

    public static String taskLimitReached(String period, String resetAt) {
        return BackendMessageCatalog.taskLimitReached(currentLocaleObject(), period, resetAt);
    }

    public static String itemLimitReached(String period, String resetAt) {
        return BackendMessageCatalog.itemLimitReached(currentLocaleObject(), period, resetAt);
    }

    private static Locale currentLocaleObject() {
        Locale holderLocale = RequestLocaleHolder.getLocale();
        if (!BackendLocaleSupport.defaultLocale().equals(holderLocale)) {
            return holderLocale;
        }

        try {
            RequestLocaleContext requestLocaleContext = currentRequestLocaleContext();
            if (requestLocaleContext != null) {
                return BackendLocaleSupport.supportedOrDefault(requestLocaleContext.getLocale());
            }
        } catch (ContextNotActiveException ex) {
            return RequestLocaleHolder.getLocale();
        }
        return RequestLocaleHolder.getLocale();
    }

    private static RequestLocaleContext currentRequestLocaleContext() {
        try {
            var container = Arc.container();
            if (container == null) {
                return null;
            }
            var instance = container.instance(RequestLocaleContext.class);
            return instance.isAvailable() ? instance.get() : null;
        } catch (IllegalStateException ex) {
            return null;
        }
    }
}
