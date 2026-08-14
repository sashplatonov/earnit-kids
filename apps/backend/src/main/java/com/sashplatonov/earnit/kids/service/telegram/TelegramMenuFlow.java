package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;

import java.util.List;

final class TelegramMenuFlow {
    private TelegramMenuFlow() {
    }

    static boolean isStartCommand(String text) {
        return text.equals("/start") || text.startsWith("/start ");
    }

    static String startText(TelegramQuickActionResponse view) {
        return view.children().size() > 1 && "parent".equals(view.role())
            ? "Choose a child" : parentOrChildText(view);
    }

    static List<TelegramBotApiClient.InlineButton> startMenu(TelegramQuickActionResponse view,
                                                              String miniAppUrl,
                                                              TelegramMenuBuilder menuBuilder) {
        return view.children().size() > 1 && "parent".equals(view.role())
            ? menuBuilder.parentChildPicker(view) : mainMenu(view, miniAppUrl, menuBuilder);
    }

    static String navigationText(String action, TelegramQuickActionResponse view) {
        if (isChildSelection(action)) {
            return parentOrChildText(view);
        }
        return switch (baseAction(action)) {
            case "child" -> "Choose a child";
            case "tasks" -> "Tasks · " + view.childName();
            case "rewards" -> "Rewards · " + view.childName();
            case "requests" -> "Requests · " + view.childName();
            case "coins" -> "Coins · " + view.childName();
            case "recent" -> "Recent · " + view.childName();
            default -> action.startsWith("coins-confirm-")
                ? coinConfirmationText(action, view) : parentOrChildText(view);
        };
    }

    private static String coinConfirmationText(String action, TelegramQuickActionResponse view) {
        int delta = coinDelta(action);
        String operation = delta > 0 ? "Add " + delta : "Remove " + Math.abs(delta);
        return operation + " coins for " + view.childName() + "?";
    }

    static List<TelegramBotApiClient.InlineButton> navigationMenu(String action,
                                                                    TelegramQuickActionResponse view,
                                                                    String miniAppUrl,
                                                                    TelegramMenuBuilder menuBuilder) {
        if (isChildSelection(action)) {
            return mainMenu(view, miniAppUrl, menuBuilder);
        }
        return switch (baseAction(action)) {
            case "child" -> "parent".equals(view.role())
                ? menuBuilder.parentChildPicker(view) : menuBuilder.backToMain();
            case "tasks", "rewards" -> "parent".equals(view.role())
                ? mainMenu(view, miniAppUrl, menuBuilder) : "tasks".equals(baseAction(action))
                    ? menuBuilder.childTasks(view, miniAppUrl) : menuBuilder.childRewards(view, miniAppUrl);
            case "requests" -> "parent".equals(view.role())
                ? menuBuilder.parentRequests(view) : mainMenu(view, miniAppUrl, menuBuilder);
            case "recent" -> menuBuilder.recent(view);
            case "coins" -> menuBuilder.parentCoins(view);
            default -> action.startsWith("coins-confirm-")
                ? menuBuilder.parentCoinConfirmation(view, coinDelta(action))
                : mainMenu(view, miniAppUrl, menuBuilder);
        };
    }

    private static String baseAction(String action) {
        int marker = action.indexOf("-child-");
        return marker >= 0 ? action.substring(0, marker) : action;
    }

    private static boolean isChildSelection(String action) {
        return action.startsWith("child-");
    }

    static int coinDelta(String action) {
        String[] parts = action.split("-");
        if (parts.length != 6 || !"coins".equals(parts[0]) || !"confirm".equals(parts[1])) {
            throw new IllegalArgumentException("Invalid coin action");
        }
        int amount = Integer.parseInt(parts[3]);
        return "add".equals(parts[2]) ? amount : -amount;
    }

    static int coinChildId(String action) {
        String[] parts = action.split("-");
        if (parts.length != 6 || !"child".equals(parts[4])) {
            throw new IllegalArgumentException("Invalid coin child action");
        }
        return Integer.parseInt(parts[5]);
    }

    static Integer selectedChildId(String action) {
        if (action == null) {
            return null;
        }
        if (action.startsWith("child-")) {
            return Integer.valueOf(action.substring("child-".length()));
        }
        int marker = action.indexOf("-child-");
        return marker >= 0 ? Integer.valueOf(action.substring(marker + 7)) : null;
    }

    private static List<TelegramBotApiClient.InlineButton> mainMenu(TelegramQuickActionResponse view,
                                                                      String miniAppUrl,
                                                                      TelegramMenuBuilder menuBuilder) {
        return "child".equals(view.role()) ? menuBuilder.childMain(view, miniAppUrl)
            : menuBuilder.parentMain(view, miniAppUrl);
    }

    private static String parentOrChildText(TelegramQuickActionResponse view) {
        return "EarnIt Kids · " + view.childName() + "\nBalance: " + view.balance() + " 🪙";
    }
}
