package com.sashplatonov.earnit.kids.service.telegram;

final class TelegramCoinCopy {
    private TelegramCoinCopy() {
    }

    static String delta(int amount, boolean earning, boolean includeName) {
        int absoluteAmount = Math.abs(amount);
        String marker = earning ? TelegramBotEmoji.COINS_EARNED : TelegramBotEmoji.COINS_SPENT;
        String sign = earning ? "+" : "-";
        return marker + " " + TelegramBotEmoji.COINS + " " + sign + absoluteAmount
            + (includeName ? " " + TelegramCopy.moneta(absoluteAmount) : "");
    }
}
