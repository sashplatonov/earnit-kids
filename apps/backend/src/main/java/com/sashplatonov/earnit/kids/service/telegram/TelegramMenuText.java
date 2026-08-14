package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;

final class TelegramMenuText {
    private TelegramMenuText() {
    }

    static String navigationText(String action, TelegramQuickActionResponse view) {
        if (action.startsWith("child-")) {
            return TelegramMenuFlow.homeText(view);
        }
        return switch (baseAction(action)) {
            case "child" -> TelegramCopy.chooseChildTitle();
            case "tasks", "rewards" -> catalogText(action, view);
            case "requests", "coins" -> parentText(baseAction(action), view);
            case "recent" -> "Recent · " + view.childName();
            case "main" -> TelegramMenuFlow.homeText(view);
            default -> unknownText(action, view);
        };
    }

    private static String catalogText(String action, TelegramQuickActionResponse view) {
        if ("parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        return "tasks".equals(baseAction(action)) ? "Tasks · " + view.childName()
            : "Rewards · " + view.childName();
    }

    private static String parentText(String action, TelegramQuickActionResponse view) {
        if (!"parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        return "requests".equals(action) ? "Requests · " + view.childName()
            : "Coins · " + view.childName();
    }

    private static String unknownText(String action, TelegramQuickActionResponse view) {
        if (!"parent".equals(view.role()) || !action.startsWith("coins-confirm-")) {
            return TelegramMenuFlow.homeText(view);
        }
        int delta = TelegramMenuFlow.coinDelta(action);
        String operation = delta > 0 ? "Add " + delta : "Remove " + Math.abs(delta);
        return operation + " coins for " + view.childName() + "?";
    }

    private static String baseAction(String action) {
        int marker = action.indexOf("-child-");
        return marker >= 0 ? action.substring(0, marker) : action;
    }
}
