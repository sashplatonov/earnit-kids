package com.sashplatonov.earnit.kids.service.telegram;

public final class TelegramParentActionCopy {
    private TelegramParentActionCopy() {
    }

    public static String taskCompleted(int delta, int balance) {
        return TelegramBotEmoji.CELEBRATE + " Родитель выполнил задание за тебя\n\n"
            + TelegramCoinCopy.delta(delta, true, true)
            + "\nБаланс: " + balance;
    }

    public static String rewardGranted(int delta, int balance) {
        return TelegramBotEmoji.CELEBRATE + " Родитель выдал награду\n\n"
            + TelegramCoinCopy.delta(delta, false, true)
            + "\nБаланс: " + balance;
    }
}
