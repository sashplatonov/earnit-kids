package com.sashplatonov.earnit.kids.service.telegram;

public final class TelegramDeepLink {
    private TelegramDeepLink() {
    }

    public static String home(String miniAppUrl) {
        return miniAppUrl;
    }

    public static String coins(String miniAppUrl) {
        return context(miniAppUrl, "coins");
    }

    public static String history(String miniAppUrl) {
        return context(miniAppUrl, "history");
    }

    public static String tasks(String miniAppUrl) {
        return context(miniAppUrl, "tasks");
    }

    public static String rewards(String miniAppUrl) {
        return context(miniAppUrl, "rewards");
    }

    private static String context(String miniAppUrl, String context) {
        if (miniAppUrl == null || miniAppUrl.isBlank()) {
            return "";
        }
        char separator = miniAppUrl.contains("?") ? '&' : '?';
        return miniAppUrl + separator + "context=" + context;
    }
}
