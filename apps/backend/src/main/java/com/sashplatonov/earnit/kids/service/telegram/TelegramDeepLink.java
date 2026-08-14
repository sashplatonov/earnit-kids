package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Exact Mini App deep links used by the bot. Role and child scope are
// EXPLAIN: still server-validated on the Mini App side; the URL never grants
// EXPLAIN: auth by itself (BUX-013).
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
