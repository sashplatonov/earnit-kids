package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TelegramMenuBuilder {
    private final TelegramCallbackService callbacks;

    @Inject
    public TelegramMenuBuilder(TelegramCallbackService callbacks) {
        this.callbacks = callbacks;
    }

    public List<TelegramBotApiClient.InlineButton> parentMain(TelegramQuickActionResponse view, String miniAppUrl) {
        return List.of(
            parentNavigation(TelegramBotEmoji.REQUEST + " Requests (" + pendingRequestCount(view) + ")", "requests", view),
            parentNavigation(TelegramBotEmoji.COINS + " Coins", "coins", view),
            parentNavigation(TelegramBotEmoji.RECENT + " Recent", "recent", view),
            parentNavigation(TelegramBotEmoji.SWITCH + " Switch child", "child", view),
            webApp(TelegramBotEmoji.OPEN_APP + " Open Mini App", miniAppUrl)
        );
    }

    public List<TelegramBotApiClient.InlineButton> parentChildPicker(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.children().stream().limit(10).forEach(child ->
            buttons.add(navigation(TelegramBotEmoji.CHILD + " " + child.name() + " · " + child.balance() + " coins", "child-" + child.id())));
        if (buttons.isEmpty()) {
            buttons.add(callback(TelegramBotEmoji.ADD + " Add child → Mini App", "noop"));
        }
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> parentNoChildren(String miniAppUrl) {
        return List.of(webApp(TelegramBotEmoji.ADD + " Add child → Mini App", miniAppUrl));
    }

    public List<TelegramBotApiClient.InlineButton> balance(TelegramQuickActionResponse view) {
        TelegramBotApiClient.InlineButton back = "parent".equals(view.role())
            ? parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view) : navigation(TelegramBotEmoji.BACK + " Back", "main");
        return List.of(callback(TelegramBotEmoji.COINS + " Balance · " + view.balance(), "noop"), back);
    }

    public List<TelegramBotApiClient.InlineButton> parentChildCatalog(TelegramQuickActionResponse view) {
        return List.of(
            parentNavigation(TelegramBotEmoji.DONE + " Tasks", "tasks", view),
            parentNavigation(TelegramBotEmoji.REWARD + " Rewards", "rewards", view),
            parentNavigation(TelegramBotEmoji.SWITCH + " Switch child", "child", view),
            parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view));
    }

    public List<TelegramBotApiClient.InlineButton> parentTasks(TelegramQuickActionResponse view) {
        return readOnlyCatalog(view.tasks().stream().limit(5)
            .map(task -> TelegramBotEmoji.DONE + " " + task.name() + " · " + task.coins() + " coins").toList(), view);
    }

    public List<TelegramBotApiClient.InlineButton> parentRewards(TelegramQuickActionResponse view) {
        return readOnlyCatalog(view.rewards().stream().limit(5)
            .map(reward -> TelegramBotEmoji.REWARD + " " + reward.name() + " · " + reward.price() + " coins").toList(), view);
    }

    public List<TelegramBotApiClient.InlineButton> parentCoins(TelegramQuickActionResponse view) {
        return List.of(
            navigation(TelegramBotEmoji.ADD + " 1", "coins-apply-add-1-child-" + view.childId()),
            navigation(TelegramBotEmoji.ADD + " 2", "coins-apply-add-2-child-" + view.childId()),
            navigation(TelegramBotEmoji.ADD + " 5", "coins-apply-add-5-child-" + view.childId()),
            navigation(TelegramBotEmoji.ADD + " 10", "coins-apply-add-10-child-" + view.childId()),
            navigation(TelegramBotEmoji.REMOVE + " 1", "coins-apply-remove-1-child-" + view.childId()),
            navigation(TelegramBotEmoji.REMOVE + " 2", "coins-apply-remove-2-child-" + view.childId()),
            navigation(TelegramBotEmoji.REMOVE + " 5", "coins-confirm-remove-5-child-" + view.childId()),
            navigation(TelegramBotEmoji.REMOVE + " 10", "coins-confirm-remove-10-child-" + view.childId()),
            parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view)
        );
    }

    public List<TelegramBotApiClient.InlineButton> parentCoinConfirmation(TelegramQuickActionResponse view,
                                                                            int delta) {
        String direction = delta > 0 ? "add" : "remove";
        int amount = Math.abs(delta);
        String target = "-child-" + view.childId();
        return List.of(
            navigation(TelegramBotEmoji.APPROVE + " Confirm", "coins-apply-" + direction + "-" + amount + target),
            parentNavigation(TelegramBotEmoji.BACK + " Cancel", "coins", view));
    }

    public List<TelegramBotApiClient.InlineButton> coinRetry(TelegramQuickActionResponse view, int delta) {
        String direction = delta > 0 ? "add" : "remove";
        int amount = Math.abs(delta);
        return List.of(
            navigation(TelegramBotEmoji.REFRESH + " Retry", "coins-apply-" + direction + "-" + amount + "-child-" + view.childId()),
            parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view));
    }

    public List<TelegramBotApiClient.InlineButton> childMain(TelegramQuickActionResponse view,
                                                              String miniAppUrl) {
        return List.of(
            navigation(TelegramBotEmoji.DONE + " Tasks", "tasks"),
            navigation(TelegramBotEmoji.REWARD + " Rewards", "rewards"),
            navigation(TelegramBotEmoji.RECENT + " Recent", "recent"),
            webApp(TelegramBotEmoji.OPEN_APP + " Open Mini App", miniAppUrl)
        );
    }

    public List<TelegramBotApiClient.InlineButton> childTasks(TelegramQuickActionResponse view,
                                                               String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.tasks().stream().limit(5).forEach(task ->
            buttons.add(callback(TelegramBotEmoji.DONE + " Done: " + task.name(), "task.request." + task.id())));
        if (view.tasks().size() > 5) {
            buttons.add(webApp(TelegramBotEmoji.OPEN_APP + " More tasks → Mini App", miniAppUrl));
        }
        buttons.add(navigation(TelegramBotEmoji.BACK + " Back", "main"));
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

    public List<TelegramBotApiClient.InlineButton> parentRequests(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.requests().stream()
            .filter(request -> request.status() == PurchaseRequestStatus.pending)
            .limit(5)
            .forEach(request -> {
                String label = request.title() == null ? "Request" : request.title();
                buttons.add(callback(TelegramBotEmoji.APPROVE + " " + label, "parent.request.approve."
                    + request.childId() + "." + request.id()));
                buttons.add(callback(TelegramBotEmoji.REJECT + " Reject", "parent.request.reject."
                    + request.childId() + "." + request.id()));
            });
        if (buttons.isEmpty()) {
            buttons.add(callback("No pending requests", "noop"));
        }
        buttons.add(parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> childRequests(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.requests().stream().limit(5).forEach(request -> {
            String label = request.title() == null ? "Request" : request.title();
            if (request.status() == PurchaseRequestStatus.rejected && request.taskId() != null) {
                buttons.add(callback(TelegramBotEmoji.REJECT + " Not approved · " + label, "noop"));
                buttons.add(callback(TelegramBotEmoji.SWITCH + " Try again", "task.request." + request.taskId()));
            } else {
                String status = request.status() == PurchaseRequestStatus.approved ? TelegramBotEmoji.DONE : TelegramBotEmoji.WAITING;
                buttons.add(callback(status + " " + label, "noop"));
            }
        });
        if (buttons.isEmpty()) {
            buttons.add(callback("No recent requests", "noop"));
        }
        buttons.add("parent".equals(view.role())
            ? parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view) : navigation(TelegramBotEmoji.BACK + " Back", "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> recent(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.history().stream().limit(5).forEach(entry -> {
            String title = entry.title() == null ? "Operation" : entry.title();
            String amount = entry.amount() >= 0 ? "+" + entry.amount() : Integer.toString(entry.amount());
            buttons.add(callback(amount + " " + TelegramBotEmoji.COINS + " · " + title, "noop"));
        });
        if (buttons.isEmpty()) {
            buttons.add(callback("No recent operations", "noop"));
        }
        buttons.add("parent".equals(view.role())
            ? parentNavigation(TelegramBotEmoji.BACK + " Back", "main", view) : navigation(TelegramBotEmoji.BACK + " Back", "main"));
        return List.copyOf(buttons);
    }

    private TelegramBotApiClient.InlineButton callback(String text, String data) {
        return TelegramBotApiClient.InlineButton.callback(text, data);
    }

    private long pendingRequestCount(TelegramQuickActionResponse view) {
        return view.requests().stream().filter(request -> request.status() == PurchaseRequestStatus.pending).count();
    }

    private List<TelegramBotApiClient.InlineButton> readOnlyCatalog(List<String> labels,
                                                                      TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        labels.forEach(label -> buttons.add(callback(label, "noop")));
        if (buttons.isEmpty()) {
            buttons.add(callback("No active entries", "noop"));
        }
        buttons.add(parentNavigation(TelegramBotEmoji.BACK + " Back", "catalog", view));
        return List.copyOf(buttons);
    }

    private TelegramBotApiClient.InlineButton navigation(String text, String action) {
        return callback(text, callbacks.signNavigation(action));
    }

    private TelegramBotApiClient.InlineButton parentNavigation(String text,
                                                                 String action,
                                                                 TelegramQuickActionResponse view) {
        return navigation(text, action + "-child-" + view.childId());
    }

    private TelegramBotApiClient.InlineButton webApp(String text, String url) {
        return TelegramBotApiClient.InlineButton.webApp(text, url);
    }
}
