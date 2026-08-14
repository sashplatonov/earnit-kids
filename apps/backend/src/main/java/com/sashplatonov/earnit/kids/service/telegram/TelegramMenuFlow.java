package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;

import java.util.List;

final class TelegramMenuFlow {
    private TelegramMenuFlow() {
    }

    static boolean isStartCommand(String text) {
        return text.equals("/start") || text.startsWith("/start ");
    }

    // EXPLAIN: /start always renders the role home. Parent Home is a decision
    // EXPLAIN: inbox for the current child; the child picker is a direct action only.
    static String startText(TelegramQuickActionResponse view) {
        return homeText(view);
    }

    static List<TelegramBotApiClient.InlineButton> startMenu(TelegramQuickActionResponse view,
                                                              String miniAppUrl,
                                                              TelegramMenuBuilder menuBuilder) {
        return mainMenu(view, miniAppUrl, menuBuilder);
    }

    // EXPLAIN: Role home text: parent sees child + balance + pending attention,
    // EXPLAIN: child sees a greeting + balance.
    static String homeText(TelegramQuickActionResponse view) {
        return "child".equals(view.role())
            ? TelegramCopy.childHome(view.childName(), view.balance())
            : TelegramCopy.parentHome(view.childName(), view.balance(), pendingCount(view));
    }

    static int pendingCount(TelegramQuickActionResponse view) {
        return (int) view.requests().stream()
            .filter(request -> request.status() == PurchaseRequestStatus.pending)
            .count();
    }

    static String navigationText(String action, TelegramQuickActionResponse view) {
        return TelegramMenuText.navigationText(action, view);
    }

    static List<TelegramBotApiClient.InlineButton> navigationMenu(String action,
                                                                    TelegramQuickActionResponse view,
                                                                    String miniAppUrl,
                                                                    TelegramMenuBuilder menuBuilder) {
        if (isChildSelection(action)) {
            return mainMenu(view, miniAppUrl, menuBuilder);
        }
        return switch (baseAction(action)) {
            case "child" -> childMenu(view, menuBuilder);
            case "tasks", "rewards" -> childCatalogMenu(action, view, miniAppUrl, menuBuilder);
            case "requests" -> requestsMenu(view, miniAppUrl, menuBuilder);
            case "recent" -> menuBuilder.recent(view);
            case "coins" -> coinsMenu(view, miniAppUrl, menuBuilder);
            default -> unknownMenu(action, view, miniAppUrl, menuBuilder);
        };
    }

    private static List<TelegramBotApiClient.InlineButton> childMenu(TelegramQuickActionResponse view,
                                                                       TelegramMenuBuilder menuBuilder) {
        return "parent".equals(view.role()) ? menuBuilder.parentChildPicker(view) : menuBuilder.backToMain();
    }

    private static List<TelegramBotApiClient.InlineButton> childCatalogMenu(String action,
                                                                              TelegramQuickActionResponse view,
                                                                              String miniAppUrl,
                                                                              TelegramMenuBuilder menuBuilder) {
        if ("parent".equals(view.role())) {
            return mainMenu(view, miniAppUrl, menuBuilder);
        }
        return "tasks".equals(baseAction(action)) ? menuBuilder.childTasks(view, miniAppUrl)
            : menuBuilder.childRewards(view, miniAppUrl);
    }

    private static List<TelegramBotApiClient.InlineButton> requestsMenu(TelegramQuickActionResponse view,
                                                                          String miniAppUrl,
                                                                          TelegramMenuBuilder menuBuilder) {
        return "parent".equals(view.role()) ? menuBuilder.parentRequests(view)
            : mainMenu(view, miniAppUrl, menuBuilder);
    }

    private static List<TelegramBotApiClient.InlineButton> coinsMenu(TelegramQuickActionResponse view,
                                                                       String miniAppUrl,
                                                                       TelegramMenuBuilder menuBuilder) {
        return "parent".equals(view.role()) ? menuBuilder.parentCoins(view)
            : mainMenu(view, miniAppUrl, menuBuilder);
    }

    private static List<TelegramBotApiClient.InlineButton> unknownMenu(String action,
                                                                         TelegramQuickActionResponse view,
                                                                         String miniAppUrl,
                                                                         TelegramMenuBuilder menuBuilder) {
        if ("parent".equals(view.role()) && action.startsWith("coins-confirm-")) {
            return menuBuilder.parentCoinConfirmation(view, coinDelta(action));
        }
        return mainMenu(view, miniAppUrl, menuBuilder);
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
}
