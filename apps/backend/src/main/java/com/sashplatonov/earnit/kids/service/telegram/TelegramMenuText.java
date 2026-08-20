package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;

import java.util.Comparator;
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
            case "child", "switch" -> TelegramCopy.chooseChildTitle();
            case "tasks", "rewards" -> catalogText(action, view);
            case "requests" -> requestsQueueText("requests", view);
            case "coins" -> coinsText(view);
            case "recent" -> TelegramRecent.format(view, java.time.Instant.now());
            case "main" -> TelegramMenuFlow.homeText(view);
            default -> unknownText(action, view);
        };
    }

    private static String requestsQueueText(String action, TelegramQuickActionResponse view) {
        if (!"parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        String currentId = action.startsWith("requests-next-")
            ? action.substring("requests-next-".length()) : null;
        List<RequestDto> pending = TelegramViewSupport.pendingRequests(view);
        int index = TelegramViewSupport.nextQueueIndex(pending, currentId);
        if (index >= pending.size()) {
            return TelegramCopy.emptyRequests();
        }
        RequestDto request = pending.get(index);
        return TelegramCopy.requestQueueText(view.childName(), TelegramViewSupport.requestTitle(request),
            request.coins(), index + 1, pending.size());
    }

    private static String catalogText(String action, TelegramQuickActionResponse view) {
        if ("parent".equals(view.role())) {
            return TelegramMenuFlow.homeText(view);
        }
        return "tasks".equals(baseAction(action)) ? tasksText(view) : rewardsText(view);
    }

    private static String tasksText(TelegramQuickActionResponse view) {
        List<TaskDto> tasks = TelegramViewSupport.orderedTasks(view);
        if (tasks.isEmpty()) {
            return TelegramCopy.emptyTasks();
        }
        StringBuilder builder = new StringBuilder(TelegramCopy.MY_TASKS);
        for (TaskDto task : tasks) {
            builder.append("\n\n").append(TelegramBotEmoji.TASK_DONE).append(" ").append(task.name())
                .append("\n").append(TelegramBotEmoji.COINS).append(" +").append(task.coins());
        }
        return builder.toString();
    }

    private static String rewardsText(TelegramQuickActionResponse view) {
        List<ShopItemDto> affordable = view.rewards().stream()
            .filter(reward -> reward.price() <= view.balance())
            .limit(3)
            .toList();
        boolean hasUnavailable = view.rewards().stream().anyMatch(reward -> reward.price() > view.balance());
        if (affordable.isEmpty() && !hasUnavailable) {
            return TelegramCopy.emptyRewards();
        }
        StringBuilder builder = new StringBuilder(TelegramCopy.REWARDS)
            .append("\n").append(TelegramBotEmoji.COINS).append(" Баланс: ").append(view.balance());
        for (ShopItemDto reward : affordable) {
            builder.append("\n\n").append(TelegramBotEmoji.REWARDS).append(" ").append(reward.name())
                .append(" · ").append(reward.price());
        }
        view.rewards().stream()
            .filter(reward -> reward.price() > view.balance())
            .min(Comparator.comparingInt(reward -> reward.price() - view.balance()))
            .ifPresent(goal -> builder.append("\n\n").append(TelegramBotEmoji.INFO).append(" Следующая цель:\n")
                .append(goal.name()).append(" · ").append(goal.price()).append("\n")
                .append("Не хватает ").append(goal.price() - view.balance()).append(" ")
                .append(TelegramCopy.moneta(goal.price() - view.balance())));
        return builder.toString();
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
