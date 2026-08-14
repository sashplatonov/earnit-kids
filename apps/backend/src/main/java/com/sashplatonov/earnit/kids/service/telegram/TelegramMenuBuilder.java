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
            parentNavigation("Child · " + view.childName(), "catalog", view),
            parentNavigation("Requests", "requests", view),
            parentNavigation("Balance · " + view.balance() + " 🪙", "balance", view),
            parentNavigation("Coins", "coins", view),
            parentNavigation("Recent", "recent", view),
            webApp("Open Mini App", miniAppUrl)
        );
    }

    public List<TelegramBotApiClient.InlineButton> parentChildPicker(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.children().stream().limit(10).forEach(child ->
            buttons.add(navigation("👧 " + child.name() + " · " + child.balance() + " 🪙", "child-" + child.id())));
        if (buttons.isEmpty()) {
            buttons.add(callback("Add child → Mini App", "noop"));
        }
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> parentNoChildren(String miniAppUrl) {
        return List.of(webApp("Add child → Mini App", miniAppUrl));
    }

    public List<TelegramBotApiClient.InlineButton> balance(TelegramQuickActionResponse view) {
        TelegramBotApiClient.InlineButton back = "parent".equals(view.role())
            ? parentNavigation("← Back", "main", view) : navigation("← Back", "main");
        return List.of(callback("Balance · " + view.balance() + " 🪙", "noop"), back);
    }

    public List<TelegramBotApiClient.InlineButton> parentChildCatalog(TelegramQuickActionResponse view) {
        return List.of(
            parentNavigation("Tasks", "tasks", view),
            parentNavigation("Rewards", "rewards", view),
            parentNavigation("Switch child", "child", view),
            parentNavigation("← Back", "main", view));
    }

    public List<TelegramBotApiClient.InlineButton> parentTasks(TelegramQuickActionResponse view) {
        return readOnlyCatalog(view.tasks().stream().limit(5)
            .map(task -> "✅ " + task.name() + " · " + task.coins() + " 🪙").toList(), view);
    }

    public List<TelegramBotApiClient.InlineButton> parentRewards(TelegramQuickActionResponse view) {
        return readOnlyCatalog(view.rewards().stream().limit(5)
            .map(reward -> "🎁 " + reward.name() + " · " + reward.price() + " 🪙").toList(), view);
    }

    public List<TelegramBotApiClient.InlineButton> parentCoins(TelegramQuickActionResponse view) {
        return List.of(
            navigation("+5 🪙", "coins-confirm-add-5-child-" + view.childId()),
            navigation("+10 🪙", "coins-confirm-add-10-child-" + view.childId()),
            navigation("−5 🪙", "coins-confirm-remove-5-child-" + view.childId()),
            navigation("−10 🪙", "coins-confirm-remove-10-child-" + view.childId()),
            parentNavigation("← Back", "main", view)
        );
    }

    public List<TelegramBotApiClient.InlineButton> parentCoinConfirmation(TelegramQuickActionResponse view,
                                                                            int delta) {
        String direction = delta > 0 ? "add" : "remove";
        int amount = Math.abs(delta);
        String target = "-child-" + view.childId();
        return List.of(
            navigation("Confirm", "coins-apply-" + direction + "-" + amount + target),
            parentNavigation("Cancel", "coins", view));
    }

    public List<TelegramBotApiClient.InlineButton> childMain(TelegramQuickActionResponse view,
                                                              String miniAppUrl) {
        return List.of(
            navigation("Balance · " + view.balance() + " 🪙", "balance"),
            navigation("Tasks", "tasks"),
            navigation("Rewards", "rewards"),
            navigation("Requests", "requests"),
            navigation("Recent", "recent"),
            webApp("Open Mini App", miniAppUrl)
        );
    }

    public List<TelegramBotApiClient.InlineButton> childTasks(TelegramQuickActionResponse view,
                                                               String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.tasks().stream().limit(5).forEach(task ->
            buttons.add(callback("✅ I did it · " + task.name(), "task.request." + task.id())));
        if (view.tasks().size() > 5) {
            buttons.add(webApp("More tasks → Mini App", miniAppUrl));
        }
        buttons.add(navigation("← Back", "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> childRewards(TelegramQuickActionResponse view,
                                                                  String miniAppUrl) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.rewards().stream().limit(5).forEach(reward ->
            buttons.add(callback("🎁 " + reward.name() + " · " + reward.price() + " 🪙",
                "reward.request." + reward.id())));
        if (view.rewards().size() > 5) {
            buttons.add(webApp("More rewards → Mini App", miniAppUrl));
        }
        buttons.add(navigation("← Back", "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> backToMain() {
        return List.of(navigation("← Back", "main"));
    }

    public List<TelegramBotApiClient.InlineButton> backToMain(TelegramQuickActionResponse view) {
        return "parent".equals(view.role())
            ? List.of(parentNavigation("← Back", "main", view)) : backToMain();
    }

    public List<TelegramBotApiClient.InlineButton> parentRequests(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.requests().stream()
            .filter(request -> request.status() == PurchaseRequestStatus.pending)
            .limit(5)
            .forEach(request -> {
                String label = request.title() == null ? "Request" : request.title();
                buttons.add(callback("✅ " + label, "parent.request.approve."
                    + request.childId() + "." + request.id()));
                buttons.add(callback("❌ Reject", "parent.request.reject."
                    + request.childId() + "." + request.id()));
            });
        if (buttons.isEmpty()) {
            buttons.add(callback("No pending requests", "noop"));
        }
        buttons.add(parentNavigation("← Back", "main", view));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> childRequests(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.requests().stream().limit(5).forEach(request -> {
            String label = request.title() == null ? "Request" : request.title();
            if (request.status() == PurchaseRequestStatus.rejected && request.taskId() != null) {
                buttons.add(callback("❌ Not approved · " + label, "noop"));
                buttons.add(callback("🔄 Try again", "task.request." + request.taskId()));
            } else {
                String status = request.status() == PurchaseRequestStatus.approved ? "✅" : "⏳";
                buttons.add(callback(status + " " + label, "noop"));
            }
        });
        if (buttons.isEmpty()) {
            buttons.add(callback("No recent requests", "noop"));
        }
        buttons.add("parent".equals(view.role())
            ? parentNavigation("← Back", "main", view) : navigation("← Back", "main"));
        return List.copyOf(buttons);
    }

    public List<TelegramBotApiClient.InlineButton> recent(TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        view.history().stream().limit(5).forEach(entry -> {
            String title = entry.title() == null ? "Operation" : entry.title();
            String amount = entry.amount() >= 0 ? "+" + entry.amount() : Integer.toString(entry.amount());
            buttons.add(callback(amount + " 🪙 · " + title, "noop"));
        });
        if (buttons.isEmpty()) {
            buttons.add(callback("No recent operations", "noop"));
        }
        buttons.add("parent".equals(view.role())
            ? parentNavigation("← Back", "main", view) : navigation("← Back", "main"));
        return List.copyOf(buttons);
    }

    private TelegramBotApiClient.InlineButton callback(String text, String data) {
        return TelegramBotApiClient.InlineButton.callback(text, data);
    }

    private List<TelegramBotApiClient.InlineButton> readOnlyCatalog(List<String> labels,
                                                                      TelegramQuickActionResponse view) {
        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        labels.forEach(label -> buttons.add(callback(label, "noop")));
        if (buttons.isEmpty()) {
            buttons.add(callback("No active entries", "noop"));
        }
        buttons.add(parentNavigation("← Back", "catalog", view));
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
