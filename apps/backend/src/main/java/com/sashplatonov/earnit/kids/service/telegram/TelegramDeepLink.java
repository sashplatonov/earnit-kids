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

    // EXPLAIN: Routes a BotNavAction.actionCode() to the corresponding deep link.
    // EXPLAIN: Falls back to the Mini App home URL for unknown codes.
    public static String build(String actionCode, String miniAppUrl) {
        if (miniAppUrl == null || miniAppUrl.isBlank()) {
            return "";
        }
        return switch (actionCode) {
            case "coins" -> coins(miniAppUrl);
            case "recent", "history" -> history(miniAppUrl);
            case "switch" -> miniAppUrl;
            case "app" -> miniAppUrl;
            case "site" -> "";
            default -> miniAppUrl;
        };
    }

    private static String context(String miniAppUrl, String context) {
        if (miniAppUrl == null || miniAppUrl.isBlank()) {
            return "";
        }
        char separator = miniAppUrl.contains("?") ? '&' : '?';
        return miniAppUrl + separator + "context=" + context;
    }
}
