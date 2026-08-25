package com.sashplatonov.earnit.kids.telegram.application.bot;

public final class TelegramParentActionCopy {
    private TelegramParentActionCopy() {
    }

    public static String taskCompleted(int delta, int balance) {
        return TelegramMessageResolverHolder.text("telegram.outcome.parentTaskCompleted", java.util.Map.of(
            "coins", TelegramCoinCopy.delta(delta, true, true), "balance", balance));
    }

    public static String rewardGranted(int delta, int balance) {
        return TelegramMessageResolverHolder.text("telegram.outcome.parentRewardGranted", java.util.Map.of(
            "coins", TelegramCoinCopy.delta(delta, false, true), "balance", balance));
    }
}
