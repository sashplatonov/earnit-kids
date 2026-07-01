package com.sashplatonov.earnit.kids.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class BackendMessageCatalog {
    private static final String BASE_NAME = "messages";
    private static final ConcurrentMap<Locale, ResourceBundle> BUNDLES = new ConcurrentHashMap<>();

    private BackendMessageCatalog() {
    }

    static String message(Locale locale, String key, Map<String, ?> variables) {
        String resolvedTemplate = lookup(locale, key);
        String resolvedMessage = resolvedTemplate;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            resolvedMessage = resolvedMessage.replace(
                "{" + entry.getKey() + "}",
                Objects.toString(entry.getValue(), "")
            );
        }
        return resolvedMessage;
    }

    static String statusTitle(Locale locale, int status) {
        return switch (status) {
            case 400 -> message(locale, "status.badRequest", Map.of());
            case 401 -> message(locale, "status.unauthorized", Map.of());
            case 403 -> message(locale, "status.forbidden", Map.of());
            case 404 -> message(locale, "status.notFound", Map.of());
            case 409 -> message(locale, "status.conflict", Map.of());
            case 500 -> message(locale, "status.internalError", Map.of());
            default -> message(locale, "status.requestFailed", Map.of());
        };
    }

    static String taskLimitReached(Locale locale, String period, String resetAt) {
        return limitReached(locale, period, "limits.taskTarget", resetAt);
    }

    static String itemLimitReached(Locale locale, String period, String resetAt) {
        return limitReached(locale, period, "limits.itemTarget", resetAt);
    }

    private static String limitReached(Locale locale, String period, String targetKey, String resetAt) {
        return message(locale, "limits." + normalizePeriod(period), Map.of(
            "target", message(locale, targetKey, Map.of()),
            "resetAt", resetAt
        ));
    }

    private static String lookup(Locale locale, String key) {
        ResourceBundle localizedBundle = bundle(locale);
        if (localizedBundle.containsKey(key)) {
            return localizedBundle.getString(key);
        }

        ResourceBundle defaultBundle = bundle(BackendLocaleSupport.defaultLocale());
        if (defaultBundle.containsKey(key)) {
            return defaultBundle.getString(key);
        }

        return key;
    }

    private static ResourceBundle bundle(Locale locale) {
        Locale normalizedLocale = BackendLocaleSupport.supportedOrDefault(locale);
        return BUNDLES.computeIfAbsent(
            normalizedLocale, currentLocale -> ResourceBundle.getBundle(BASE_NAME, currentLocale));
    }

    private static String normalizePeriod(String period) {
        return switch (period) {
            case "week", "month", "year", "season" -> period;
            default -> "day";
        };
    }
}
