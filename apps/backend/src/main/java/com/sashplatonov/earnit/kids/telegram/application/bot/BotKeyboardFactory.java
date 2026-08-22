package com.sashplatonov.earnit.kids.telegram.application.bot;

public class BotKeyboardFactory {

    private final String publicSiteUrl;

    public BotKeyboardFactory(String publicSiteUrl) {
        this.publicSiteUrl = publicSiteUrl;
    }

    public TelegramReplyKeyboard parentMain() {
        var rows = new java.util.ArrayList<TelegramReplyKeyboard.Row>();
        rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.NAV_REQUESTS, TelegramCopy.NAV_COINS));
        rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.NAV_RECENT, TelegramCopy.NAV_SELECT_CHILD));
        if (publicSiteUrl != null && !publicSiteUrl.isBlank()) {
            rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.NAV_OPEN_SITE));
        }
        return TelegramReplyKeyboard.persistent(rows);
    }

    public TelegramReplyKeyboard childMain() {
        var rows = java.util.List.of(
            new TelegramReplyKeyboard.Row(TelegramCopy.MY_TASKS, TelegramCopy.REWARDS),
            new TelegramReplyKeyboard.Row(TelegramCopy.NAV_RECENT)
        );
        return TelegramReplyKeyboard.persistent(rows);
    }
}
