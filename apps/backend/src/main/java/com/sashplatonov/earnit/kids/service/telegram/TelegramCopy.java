package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus;

// EXPLAIN: Centralized user-facing copy for the Telegram bot. Every button
// EXPLAIN: label is composed from exactly one semantic emoji taken from
// EXPLAIN: TelegramBotEmoji plus a human label. Emoji literals must never be
// EXPLAIN: embedded directly in handlers or menu builders. Message templates
// EXPLAIN: live here so the bot reads as a Telegram-native companion rather
// EXPLAIN: than a web menu tree.
public final class TelegramCopy {
    private TelegramCopy() {
    }

    // EXPLAIN: Button labels: exactly one semantic emoji each.
    public static final String HOME = TelegramBotEmoji.HOME + " Главное меню";
    public static final String MY_TASKS = TelegramBotEmoji.TASKS + " Мои задания";
    public static final String REWARDS = TelegramBotEmoji.REWARDS + " Награды";
    public static final String REQUESTS = TelegramBotEmoji.REQUESTS + " Запросы";
    public static final String COINS = TelegramBotEmoji.COINS + " Монеты";
    public static final String RECENT = TelegramBotEmoji.RECENT + " Последние";
    public static final String SWITCH_CHILD = TelegramBotEmoji.CHILD + " Выбрать ребёнка";
    public static final String ADD_CHILD_MINI_APP = TelegramBotEmoji.ADD + " Добавить ребёнка → Mini App";
    public static final String OPEN_APP = TelegramBotEmoji.MINI_APP + " Открыть приложение";
    public static final String APPROVE = TelegramBotEmoji.APPROVE + " Одобрить";
    public static final String REJECT = TelegramBotEmoji.REJECT + " Отклонить";
    public static final String NEXT = TelegramBotEmoji.NEXT + " Следующий";
    public static final String CUSTOM_AMOUNT = TelegramBotEmoji.CUSTOM + " Другая сумма";
    public static final String FULL_HISTORY = TelegramBotEmoji.MINI_APP + " Полная история";
    public static final String ALL_TASKS = TelegramBotEmoji.MINI_APP + " Все задания";
    public static final String ALL_REWARDS = TelegramBotEmoji.MINI_APP + " Все награды";
    public static final String RETRY = TelegramBotEmoji.REFRESH + " Повторить";
    public static final String CONFIRM = TelegramBotEmoji.APPROVE + " Подтвердить";
    public static final String CANCEL = TelegramBotEmoji.REJECT + " Отмена";
    public static final String SHARE_SITE = TelegramBotEmoji.LINK + " Публичный сайт";

    // EXPLAIN: Persistent reply keyboard labels (UX-01). Short labels keep the
    // EXPLAIN: bottom row balanced between two equal-width buttons.
    public static final String NAV_REQUESTS = TelegramBotEmoji.REQUESTS + " Запросы";
    public static final String NAV_COINS = TelegramBotEmoji.COINS + " Монеты";
    public static final String NAV_RECENT = TelegramBotEmoji.RECENT + " Последние";
    public static final String NAV_SELECT_CHILD = TelegramBotEmoji.CHILD + " Выбрать ребёнка";
    public static final String NAV_OPEN_SITE = TelegramBotEmoji.SITE + " Сайт";

    // EXPLAIN: Dynamic button labels.
    public static String coinAdd(int amount) {
        return TelegramBotEmoji.ADD + " +" + amount;
    }

    public static String coinRemove(int amount) {
        return TelegramBotEmoji.REMOVE + " -" + amount;
    }

    public static String doneTask(String taskName) {
        return TelegramBotEmoji.TASKS + " Готово: " + taskName;
    }

    public static String getReward(String rewardName) {
        return TelegramBotEmoji.REWARDS + " Получить: " + rewardName;
    }

    public static String chooseChild(String childName, int balance) {
        return TelegramBotEmoji.CHILD + " " + childName + " · " + balance;
    }

    // EXPLAIN: Title of the flat child picker screen.
    public static String chooseChildTitle() {
        return TelegramBotEmoji.CHILD + " Кого показывать?";
    }

    // EXPLAIN: Message templates.

    // EXPLAIN: Parent Home decision menu.
    public static String parentHome(String childName, int balance, int pending) {
        String body = TelegramBotEmoji.CHILD + " " + childName + "\n" + coinsLine(balance);
        String attention = pending > 0
            ? "\n\n" + TelegramBotEmoji.REQUESTS + " Требуют внимания: " + pending
            : "\n\n" + TelegramBotEmoji.SUCCESS + " Сейчас ничего не требует внимания";
        return body + attention;
    }

    // EXPLAIN: Parent coins quick-action screen.
    public static String parentCoins(String childName, int balance) {
        return TelegramBotEmoji.CHILD + " " + childName + "\n"
            + TelegramBotEmoji.COINS + " Баланс: " + balance + "\n\n"
            + TelegramBotEmoji.ADD + " Добавить монеты\n"
            + TelegramBotEmoji.REMOVE + " Снять монеты";
    }

    // EXPLAIN: Parent coins immediate-action feedback, keeps the quick-action keyboard.
    public static String coinApplied(int delta, int balance) {
        String verb = delta > 0 ? "Добавлено" : "Снято";
        return TelegramBotEmoji.SUCCESS + " " + verb + " " + Math.abs(delta) + " "
            + moneta(Math.abs(delta)) + "\n" + coinsLine(balance);
    }

    // EXPLAIN: Confirmation prompt protecting high negative adjustments.
    public static String coinConfirmText(String childName, int delta) {
        int amount = Math.abs(delta);
        String verb = delta > 0 ? "Добавить" : "Снять";
        String preposition = delta > 0 ? " для " : " с ";
        return verb + " " + amount + " " + moneta(amount) + preposition + childName + "?";
    }

    // EXPLAIN: Child greeting Home.
    public static String childHome(String childName, int balance) {
        return TelegramBotEmoji.GREETING + " " + childName + "\n" + coinsLine(balance);
    }

    // EXPLAIN: One-at-a-time Requests queue header.
    public static String requestQueue(int index, int total) {
        return TelegramBotEmoji.REQUESTS + " Запрос " + index + " из " + total;
    }

    // EXPLAIN: Full queue screen text for one pending request.
    public static String requestQueueText(String childName, String title, int coins, int index, int total) {
        return requestQueue(index, total) + "\n\n" + TelegramBotEmoji.CHILD + " " + childName + "\n\n"
            + title + "\n" + TelegramBotEmoji.COINS + " +" + coins + " " + moneta(coins);
    }

    // EXPLAIN: Pending request body used on the approval notification.
    public static String requestNotification(String childName, String title, int coins, boolean task) {
        String lead = task ? " выполнила:" : " хочет награду:";
        return TelegramBotEmoji.CHILD + " " + childName + lead + "\n\n"
            + title + "\n" + TelegramBotEmoji.COINS + " +" + coins + " " + moneta(coins);
    }

    // EXPLAIN: Approved task outcome shown to the parent.
    public static String parentApproved(String title, int delta, int balance) {
        String result = TelegramBotEmoji.SUCCESS + " Одобрено\n\n" + title;
        if (delta != 0) {
            result += "\n" + TelegramBotEmoji.COINS + " +" + delta + " " + moneta(delta);
        }
        result += "\nБаланс: " + balance;
        return result;
    }

    // EXPLAIN: Rejected request outcome shown to the parent.
    public static String parentRejected() {
        return TelegramBotEmoji.REJECT + " Отклонено";
    }

    // EXPLAIN: Stale decision feedback shown instead of a second mutation.
    public static String stale() {
        return TelegramBotEmoji.INFO + " Этот запрос уже обработан";
    }

    // EXPLAIN: Final status line appended to a resolved request message. The
    // EXPLAIN: message keeps its original body and gains a terminal status with
    // EXPLAIN: no approve/reject buttons.
    public static String requestResolved(String title, RequestResolutionStatus status) {
        String statusLine = switch (status) {
            case approved -> TelegramBotEmoji.SUCCESS + " Одобрено";
            case rejected -> TelegramBotEmoji.DECLINE + " Отклонено";
            case cancelled -> TelegramBotEmoji.CANCEL + " Отменено";
            case deleted -> TelegramBotEmoji.DELETE + " Удалено";
        };
        return title == null || title.isBlank() ? statusLine : title + "\n" + statusLine;
    }

    // EXPLAIN: Generic action failure with retry-safe copy.
    public static String error() {
        return TelegramBotEmoji.ERROR + " Не удалось выполнить действие\nПопробуйте ещё раз";
    }

    // EXPLAIN: Child waiting state after submitting a Done request.
    public static String waiting(String taskName) {
        return TelegramBotEmoji.WAITING + " " + taskName + "\nЖдём решения родителя";
    }

    // EXPLAIN: Child reward request submitted.
    public static String rewardWaiting() {
        return TelegramBotEmoji.WAITING + " Заявка отправлена родителю";
    }

    // EXPLAIN: Child task approved feedback.
    public static String childTaskApproved(String title, int delta, int balance) {
        return TelegramBotEmoji.CELEBRATE + " " + title + " одобрен\n\n"
            + TelegramBotEmoji.COINS + " +" + delta + " " + moneta(delta) + "\nБаланс: " + balance;
    }

    // EXPLAIN: Child reward approved feedback.
    public static String childRewardApproved(String title) {
        return TelegramBotEmoji.CELEBRATE + " Награда одобрена\n\n" + title;
    }

    // EXPLAIN: Child task rejected feedback.
    public static String childTaskRejected(String title) {
        return TelegramBotEmoji.DECLINE + " " + title + " не одобрен";
    }

    // EXPLAIN: Child reward rejected feedback.
    public static String childRewardRejected(String title) {
        return TelegramBotEmoji.DECLINE + " Награда не одобрена\n\n" + title;
    }

    // EXPLAIN: Empty / informational states.
    public static String emptyRequests() {
        return TelegramBotEmoji.SUCCESS + " Нет запросов, ожидающих решения";
    }

    public static String emptyTasks() {
        return TelegramBotEmoji.SUCCESS + " На сегодня активных заданий нет";
    }

    public static String emptyRewards() {
        return TelegramBotEmoji.REWARDS + " Сейчас нет доступных наград";
    }

    public static String emptyRecent() {
        return TelegramBotEmoji.SUCCESS + " Пока нет событий";
    }

    // EXPLAIN: Helpers.

    // EXPLAIN: "🪙 N монета/монеты/монет" with correct Russian plural form.
    public static String coinsLine(int n) {
        return TelegramBotEmoji.COINS + " " + n + " " + moneta(n);
    }

    // EXPLAIN: Russian plural for "монета".
    public static String moneta(int n) {
        int abs = Math.abs(n) % 100;
        int last = abs % 10;
        if (abs > 10 && abs < 20) {
            return "монет";
        }
        if (last == 1) {
            return "монета";
        }
        if (last >= 2 && last <= 4) {
            return "монеты";
        }
        return "монет";
    }
}
