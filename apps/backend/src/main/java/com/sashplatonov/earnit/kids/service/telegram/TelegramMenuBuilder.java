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

    public List<TelegramBotApiClient.InlineButton> parentMain(TelegramQuickActionResponse view, String miniAppUrl) {
        // EXPLAIN: Decision menu: two-column grid, Mini App is always the last row.
        return List.of(
            parentNavigation(TelegramCopy.REQUESTS, "requests", view, "home-row-1"),
            parentNavigation(TelegramCopy.COINS, "coins", view, "home-row-1"),
            parentNavigation(TelegramCopy.RECENT, "recent", view, "home-row-2"),
            parentNavigation(TelegramCopy.SWITCH_CHILD, "child", view, "home-row-2"),
            webApp(TelegramCopy.OPEN_APP, miniAppUrl, "home-row-3")
        );
    }

    public List<TelegramBotApiClient.InlineButton> parentChildPicker(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.children().stream().limit(10).forEach(child ->
            buttons.add(navigation(TelegramCopy.chooseChild(child.name(), child.balance()), "child-" + child.id())));
        if (buttons.isEmpty()) {
            buttons.add(callback(TelegramBotEmoji.ADD + " Add child → Mini App", "noop"));
        }
        buttons.add(navigation(TelegramCopy.HOME, "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> parentNoChildren(String miniAppUrl) {
        return List.of(webApp(TelegramBotEmoji.ADD + " Add child → Mini App", miniAppUrl));
    }

    public List<TelegramBotApiClient.InlineButton> parentCoins(TelegramQuickActionResponse view,
                                                                 String miniAppUrl) {
        // EXPLAIN: Fixed +1/+2/+5/+10 and -1/-2 apply immediately; -5/-10 ask
        // EXPLAIN: for confirmation; a custom amount deep-links into the Mini App.
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

    public List<TelegramBotApiClient.InlineButton> coinRetry(TelegramQuickActionResponse view, int delta) {
        String direction = delta > 0 ? "add" : "remove";
        int amount = Math.abs(delta);
        return List.of(
            navigation(TelegramCopy.RETRY, "coins-apply-" + direction + "-" + amount + "-child-" + view.childId(),
                "retry-row-1"),
            parentNavigation(TelegramCopy.HOME, "main", view, "retry-row-2"));
    }

    // EXPLAIN: Child Home is a short action companion: tasks, rewards, recent
    // EXPLAIN: and the Mini App. No parent-only controls are reachable here.
    public List<TelegramBotApiClient.InlineButton> childMain(TelegramQuickActionResponse view,
                                                              String miniAppUrl) {
        return List.of(
            navigation(TelegramCopy.MY_TASKS, "tasks"),
            navigation(TelegramCopy.REWARDS, "rewards"),
            navigation(TelegramCopy.RECENT, "recent"),
            webApp(TelegramCopy.OPEN_APP, miniAppUrl)
        );
    }

    // EXPLAIN: Action-first: Done buttons only for available (non-pending)
    // EXPLAIN: tasks, capped at five; more opens the Child Mini App Today.
    public List<TelegramBotApiClient.InlineButton> childTasks(TelegramQuickActionResponse view,
                                                               String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        Set<Long> pending = TelegramMenuFlow.pendingTaskIds(view);
        TelegramMenuFlow.orderedTasks(view).stream()
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
        view.rewards().stream().filter(reward -> reward.price() <= view.balance()).limit(3).forEach(reward ->
            buttons.add(callback(TelegramBotEmoji.REWARD + " Get " + reward.name() + " · " + reward.price() + " coins",
                "reward.request." + reward.id())));
        view.rewards().stream().filter(reward -> reward.price() > view.balance()).min(java.util.Comparator.comparingInt(reward -> reward.price() - view.balance())).ifPresent(reward ->
            buttons.add(callback(TelegramBotEmoji.WAITING + " Next goal: " + reward.name() + " · " + reward.price() + " coins", "noop")));
        if (view.rewards().stream().filter(reward -> reward.price() <= view.balance()).count() > 3
            || view.rewards().size() > 4) {
            buttons.add(webApp(TelegramBotEmoji.OPEN_APP + " More rewards → Mini App", miniAppUrl));
        }
        buttons.add(navigation(TelegramBotEmoji.BACK + " Back", "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> backToMain() {
        return List.of(navigation(TelegramBotEmoji.BACK + " Back", "main"));
    }

    public List<TelegramBotApiClient.InlineButton> backToMain(TelegramQuickActionResponse view) {
        return "parent".equals(view.role())
            ? List.of(parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view)) : backToMain();
    }

    // EXPLAIN: One pending request at a time; auto-advances or ends after decision.
    public List<TelegramBotApiClient.InlineButton> parentRequestQueue(TelegramQuickActionResponse view,
                                                                        String currentRequestId) {
        List<RequestDto> pending = TelegramMenuFlow.pendingRequests(view);
        int index = TelegramMenuFlow.nextQueueIndex(pending, currentRequestId);
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
            buttons.add(navigation(TelegramCopy.NEXT, "requests-next-" + request.id(), "queue-row-2"));
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

    // EXPLAIN: Recent stays a preview; the rows live in the message body and the
    // EXPLAIN: full history opens as a Mini App deep link instead of paginating.
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
}
