package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Russian copy for notifications about direct parent actions (a parent
// EXPLAIN: completes a task or grants a reward without a child request). Kept
// EXPLAIN: out of TelegramCopy to stay under the PMD TooManyMethods guardrail.
public final class TelegramParentActionCopy {
    private TelegramParentActionCopy() {
    }

    public static String taskCompleted(int delta, int balance) {
        return TelegramBotEmoji.CELEBRATE + " Родитель выполнил задание за тебя\n\n"
            + TelegramBotEmoji.COINS + " +" + delta + " " + TelegramCopy.moneta(delta)
            + "\nБаланс: " + balance;
    }

    public static String rewardGranted(int delta, int balance) {
        return TelegramBotEmoji.CELEBRATE + " Родитель выдал награду\n\n"
            + TelegramBotEmoji.COINS + " " + delta + " " + TelegramCopy.moneta(delta)
            + "\nБаланс: " + balance;
    }
}
