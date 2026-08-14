package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;

import java.util.List;

final class TelegramMenuText {
    private TelegramMenuText() {
    }

    static String navigationText(String action, TelegramQuickActionResponse view) {
        if (action.startsWith("child-")) {
            return TelegramMenuFlow.homeText(view);
        }
        String base = baseAction(action);
        if (base.startsWith("requests-next-")) {
            return requestsQueueText(base, view);
        }
        return switch (base) {
            case "child" -> TelegramCopy.chooseChildTitle();
            case "tasks", "rewards" -> catalogText(action, view);
            case "requests" -> requestsQueueText("requests", view);
            case "coins" -> coinsText(view);
            case "recent" -> TelegramRecent.format(view, java.time.Instant.now());
            case "main" -> TelegramMenuFlow.homeText(view);
            default -> unknownText(action, view);
        };
    }

    // EXPLAIN: One pending request at a time with a bounded queue header.
    private static String requestsQueueText(String action, TelegramQuickActionResponse view) {
        if (!"parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        String currentId = action.startsWith("requests-next-")
            ? action.substring("requests-next-".length()) : null;
        List<RequestDto> pending = TelegramMenuFlow.pendingRequests(view);
        int index = TelegramMenuFlow.nextQueueIndex(pending, currentId);
        if (index >= pending.size()) {
            return TelegramCopy.emptyRequests();
        }
        RequestDto request = pending.get(index);
        return TelegramCopy.requestQueueText(view.childName(), TelegramMenuFlow.requestTitle(request),
            request.coins(), index + 1, pending.size());
    }

    private static String catalogText(String action, TelegramQuickActionResponse view) {
        if ("parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        return "tasks".equals(baseAction(action)) ? "Tasks · " + view.childName()
            : "Rewards · " + view.childName();
    }

    private static String coinsText(TelegramQuickActionResponse view) {
        if (!"parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        return TelegramCopy.parentCoins(view.childName(), view.balance());
    }

    private static String unknownText(String action, TelegramQuickActionResponse view) {
        if (!"parent".equals(view.role()) || !action.startsWith("coins-confirm-")) {
            return TelegramMenuFlow.homeText(view);
        }
        int delta = TelegramMenuFlow.coinDelta(action);
        return TelegramCopy.coinConfirmText(view.childName(), delta);
    }

    private static String baseAction(String action) {
        int marker = action.indexOf("-child-");
        return marker >= 0 ? action.substring(0, marker) : action;
    }
}
