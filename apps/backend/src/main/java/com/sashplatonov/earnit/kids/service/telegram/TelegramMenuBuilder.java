package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TelegramMenuBuilder {
    private final TelegramCallbackService callbacks;

    @Inject
    public TelegramMenuBuilder(TelegramCallbackService callbacks) {
        this.callbacks = callbacks;
    }

    public List<TelegramBotApiClient.InlineButton> parentMain(TelegramQuickActionResponse view,
                                                               String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        buttons.add(parentNavigation(TelegramCopy.REQUESTS, "requests", view, "home-row-1"));
        buttons.add(parentNavigation(TelegramCopy.COINS, "coins", view, "home-row-1"));
        buttons.add(parentNavigation(TelegramCopy.RECENT, "recent", view, "home-row-2"));
        buttons.add(parentNavigation(TelegramCopy.SWITCH_CHILD, "switch", view, "home-row-2"));
        buttons.add(webApp(TelegramCopy.OPEN_APP, miniAppUrl, "home-row-3"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> parentChildPicker(TelegramQuickActionResponse view,
                                                                       String publicSiteUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.children().stream().limit(10).forEach(child ->
            buttons.add(navigation(TelegramCopy.chooseChild(child.name(), child.balance()), "child-" + child.id())));
        if (buttons.isEmpty()) {
            buttons.add(callback(TelegramCopy.ADD_CHILD_MINI_APP, "noop"));
        }
        buttons.add(navigation(TelegramCopy.HOME, "main"));
        if (publicSiteUrl != null && !publicSiteUrl.isBlank()) {
            buttons.add(url(TelegramCopy.SHARE_SITE, publicSiteUrl, "picker-row-4"));
        }
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> parentNoChildren(String miniAppUrl) {
        return List.of(webApp(TelegramCopy.ADD_CHILD_MINI_APP, miniAppUrl));
    }

    public List<TelegramBotApiClient.InlineButton> parentCoins(TelegramQuickActionResponse view,
                                                                 String miniAppUrl) {
        return List.of(
            navigation(TelegramCopy.coinAdd(1), "coins-apply-add-1-child-" + view.childId(), "coins-row-1"),
            navigation(TelegramCopy.coinAdd(2), "coins-apply-add-2-child-" + view.childId(), "coins-row-1"),
            navigation(TelegramCopy.coinAdd(5), "coins-apply-add-5-child-" + view.childId(), "coins-row-2"),
            navigation(TelegramCopy.coinAdd(10), "coins-apply-add-10-child-" + view.childId(), "coins-row-2"),
            navigation(TelegramCopy.coinRemove(1), "coins-apply-remove-1-child-" + view.childId(), "coins-row-3"),
            navigation(TelegramCopy.coinRemove(2), "coins-apply-remove-2-child-" + view.childId(), "coins-row-3"),
            navigation(TelegramCopy.coinRemove(5), "coins-confirm-remove-5-child-" + view.childId(), "coins-row-4"),
            navigation(TelegramCopy.coinRemove(10), "coins-confirm-remove-10-child-" + view.childId(), "coins-row-4"),
            webApp(TelegramCopy.CUSTOM_AMOUNT, TelegramDeepLink.coins(miniAppUrl), "coins-row-5"),
            parentNavigation(TelegramCopy.HOME, "main", view, "coins-row-6")
        );
    }

    public List<TelegramBotApiClient.InlineButton> parentCoinConfirmation(TelegramQuickActionResponse view,
                                                                            int delta) {
        String direction = delta > 0 ? "add" : "remove";
        int amount = Math.abs(delta);
        String target = "-child-" + view.childId();
        return List.of(
            navigation(TelegramCopy.CONFIRM, "coins-apply-" + direction + "-" + amount + target, "confirm-row-1"),
            parentNavigation(TelegramCopy.HOME, "main", view, "confirm-row-2"));
    }

    public List<TelegramBotApiClient.InlineButton> parentRequestRetry(TelegramQuickActionResponse view,
                                                                       String retryData) {
        return List.of(
            callback(TelegramCopy.RETRY, retryData),
            parentNavigation(TelegramCopy.HOME, "main", view));
    }

    public List<TelegramBotApiClient.InlineButton> childRetry(String retryData) {
        return List.of(
            callback(TelegramCopy.RETRY, retryData),
            navigation(TelegramCopy.HOME, "main"));
    }

    public List<TelegramBotApiClient.InlineButton> coinRetry(TelegramQuickActionResponse view, int delta) {
        String direction = delta > 0 ? "add" : "remove";
        int amount = Math.abs(delta);
        return List.of(
            navigation(TelegramCopy.RETRY, "coins-apply-" + direction + "-" + amount + "-child-" + view.childId(),
                "retry-row-1"),
            parentNavigation(TelegramCopy.HOME, "main", view, "retry-row-2"));
    }

    public List<TelegramBotApiClient.InlineButton> childMain(TelegramQuickActionResponse view,
                                                              String miniAppUrl) {
        return List.of(
            navigation(TelegramCopy.MY_TASKS, "tasks"),
            navigation(TelegramCopy.REWARDS, "rewards"),
            navigation(TelegramCopy.RECENT, "recent"),
            webApp(TelegramCopy.OPEN_APP, miniAppUrl)
        );
    }

    public List<TelegramBotApiClient.InlineButton> childTasks(TelegramQuickActionResponse view,
                                                               String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        Set<Long> pending = TelegramViewSupport.pendingTaskIds(view);
        TelegramViewSupport.orderedTasks(view).stream()
            .filter(task -> !pending.contains(task.id()))
            .forEach(task -> buttons.add(callback(TelegramCopy.doneTask(task.name()), "task.request." + task.id())));
        if (view.tasks().size() > 5) {
            buttons.add(webApp(TelegramCopy.ALL_TASKS, TelegramDeepLink.tasks(miniAppUrl)));
        }
        buttons.add(navigation(TelegramCopy.HOME, "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> childRewards(TelegramQuickActionResponse view,
                                                                  String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.rewards().stream()
            .filter(reward -> reward.price() <= view.balance())
            .limit(3)
            .forEach(reward -> buttons.add(callback(TelegramCopy.getReward(reward.name()),
                "reward.request." + reward.id())));
        if (hasMoreRewards(view)) {
            buttons.add(webApp(TelegramCopy.ALL_REWARDS, TelegramDeepLink.rewards(miniAppUrl)));
        }
        buttons.add(navigation(TelegramCopy.HOME, "main"));
        return List.copyOf(buttons);
    }

    private boolean hasMoreRewards(TelegramQuickActionResponse view) {
        long affordable = view.rewards().stream()
            .filter(reward -> reward.price() <= view.balance()).count();
        return affordable > 3 || view.rewards().size() > 4;
    }

    public List<TelegramBotApiClient.InlineButton> backToMain() {
        return List.of(navigation(TelegramCopy.HOME, "main"));
    }

    public List<TelegramBotApiClient.InlineButton> backToMain(TelegramQuickActionResponse view) {
        return "parent".equals(view.role())
            ? List.of(parentNavigation(TelegramCopy.HOME, "main", view)) : backToMain();
    }

    public List<TelegramBotApiClient.InlineButton> parentRequestQueue(TelegramQuickActionResponse view,
                                                                        String currentRequestId) {
        List<RequestDto> pending = TelegramViewSupport.pendingRequests(view);
        int index = TelegramViewSupport.nextQueueIndex(pending, currentRequestId);
        if (index >= pending.size()) {
            return List.of();
        }
        RequestDto request = pending.get(index);
        int total = pending.size();
        String target = request.childId() + "." + request.id() + ".queue";
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        buttons.add(callback(TelegramCopy.APPROVE, "parent.request.approve." + target, "queue-row-1"));
        buttons.add(callback(TelegramCopy.REJECT, "parent.request.reject." + target, "queue-row-1"));
        if (index + 1 < total) {
            buttons.add(parentNavigation(TelegramCopy.NEXT, "requests-next-" + request.id(), view, "queue-row-2"));
        }
        buttons.add(parentNavigation(TelegramCopy.HOME, "main", view, "queue-row-3"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> parentRequestsEmpty(TelegramQuickActionResponse view,
                                                                        String miniAppUrl) {
        return List.of(
            parentNavigation(TelegramCopy.HOME, "main", view),
            webApp(TelegramCopy.OPEN_APP, miniAppUrl));
    }

    public List<TelegramBotApiClient.InlineButton> recent(TelegramQuickActionResponse view,
                                                            String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        if ("parent".equals(view.role())) {
            buttons.add(webApp(TelegramCopy.FULL_HISTORY, TelegramDeepLink.history(miniAppUrl)));
        }
        buttons.add("parent".equals(view.role())
            ? parentNavigation(TelegramCopy.HOME, "main", view)
            : navigation(TelegramCopy.HOME, "main"));
        return List.copyOf(buttons);
    }

    private TelegramBotApiClient.InlineButton callback(String text, String data) {
        return TelegramBotApiClient.InlineButton.callback(text, data);
    }

    private TelegramBotApiClient.InlineButton callback(String text, String data, String rowId) {
        return TelegramBotApiClient.InlineButton.callback(text, data, rowId);
    }

    private TelegramBotApiClient.InlineButton navigation(String text, String action) {
        return callback(text, callbacks.signNavigation(action));
    }

    private TelegramBotApiClient.InlineButton navigation(String text, String action, String rowId) {
        return callback(text, callbacks.signNavigation(action), rowId);
    }

    private TelegramBotApiClient.InlineButton parentNavigation(String text,
                                                                 String action,
                                                                 TelegramQuickActionResponse view) {
        return navigation(text, action + "-child-" + view.childId());
    }

    private TelegramBotApiClient.InlineButton parentNavigation(String text,
                                                                 String action,
                                                                 TelegramQuickActionResponse view,
                                                                 String rowId) {
        return navigation(text, action + "-child-" + view.childId(), rowId);
    }

    private TelegramBotApiClient.InlineButton webApp(String text, String url) {
        return TelegramBotApiClient.InlineButton.webApp(text, url);
    }

    private TelegramBotApiClient.InlineButton webApp(String text, String url, String rowId) {
        return TelegramBotApiClient.InlineButton.webApp(text, url, rowId);
    }

    private TelegramBotApiClient.InlineButton url(String text, String url, String rowId) {
        return TelegramBotApiClient.InlineButton.url(text, url, rowId);
    }
}
