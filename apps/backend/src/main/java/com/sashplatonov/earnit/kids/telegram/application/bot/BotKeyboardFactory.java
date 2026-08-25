package com.sashplatonov.earnit.kids.telegram.application.bot;

public class BotKeyboardFactory {

    private final String publicSiteUrl;

    public BotKeyboardFactory(String publicSiteUrl) {
        this.publicSiteUrl = publicSiteUrl;
    }

    public TelegramReplyKeyboard parentMain() {
        var rows = new java.util.ArrayList<TelegramReplyKeyboard.Row>();
        rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.requests(TelegramLocaleContext.current()), TelegramCopy.coins(TelegramLocaleContext.current())));
        rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.recent(TelegramLocaleContext.current()), TelegramCopy.switchChild(TelegramLocaleContext.current())));
        if (publicSiteUrl != null && !publicSiteUrl.isBlank()) {
            rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.site(TelegramLocaleContext.current())));
        }
        return TelegramReplyKeyboard.persistent(rows);
    }

    public TelegramReplyKeyboard childMain() {
        var rows = java.util.List.of(
            new TelegramReplyKeyboard.Row(TelegramCopy.myTasks(TelegramLocaleContext.current()), TelegramCopy.rewards(TelegramLocaleContext.current())),
            new TelegramReplyKeyboard.Row(TelegramCopy.recent(TelegramLocaleContext.current()))
        );
        return TelegramReplyKeyboard.persistent(rows);
    }
}
