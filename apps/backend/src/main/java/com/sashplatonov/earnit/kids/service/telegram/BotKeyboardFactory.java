package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Builds persistent ReplyKeyboardMarkup for the global navigation bar.
// EXPLAIN: UX-01 — two-row layout: [Запросы | Монеты] and [Последние |
// EXPLAIN: Выбрать] on the parent side, [Мои задания | Награды] and [Последние |
// EXPLAIN: Открыть] on the child side.
public class BotKeyboardFactory {

    // EXPLAIN: Called by the backend to resolve the public-site URL for the
    // EXPLAIN: NAV_OPEN_SITE button. If null the site button is excluded.
    private final String publicSiteUrl;

    // EXPLAIN: Mini App URL used to build the web_app button for NAV_OPEN_APP.
    private final String miniAppUrl;

    // EXPLAIN: Bot username used to build the Telegram deep-link URL for the
    // EXPLAIN: web_app button. Without this, Telegram opens the URL without
    // EXPLAIN: injecting initData (needed for Mini App auth).
    private final String botUsername;

    public BotKeyboardFactory(String publicSiteUrl, String miniAppUrl, String botUsername) {
        this.publicSiteUrl = publicSiteUrl;
        this.miniAppUrl = miniAppUrl;
        this.botUsername = botUsername;
    }

    // EXPLAIN: Returns a Telegram deep-link URL for the Mini App. Format:
    // EXPLAIN: https://t.me/<bot>?startapp=<mini-app-url>
    // EXPLAIN: This is required for Telegram to inject initData into the WebView.
    private String deepLinkUrl(String miniAppUrl) {
        if (miniAppUrl == null || miniAppUrl.isBlank() || botUsername == null || botUsername.isBlank()) {
            return miniAppUrl;
        }
        return "https://t.me/" + java.net.URLEncoder.encode(botUsername, java.nio.charset.StandardCharsets.UTF_8)
            + "?startapp=" + java.net.URLEncoder.encode(miniAppUrl, java.nio.charset.StandardCharsets.UTF_8);
    }

    // EXPLAIN: Returns a reply keyboard for the parent main view.
    // EXPLAIN: Two rows: [Запросы | Монеты] and [Последние | Выбрать].
    // EXPLAIN: Third row: [Приложение | Сайт] when publicSiteUrl is set.
    public TelegramReplyKeyboard parentMain() {
        // EXPLAIN: Two-row layout: [Запросы | Монеты] and [Последние | Выбрать].
        // EXPLAIN: Third row: [Приложение | Сайт] when publicSiteUrl is set.
        var rows = new java.util.ArrayList<TelegramReplyKeyboard.Row>();
        rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.NAV_REQUESTS, TelegramCopy.NAV_COINS));
        rows.add(new TelegramReplyKeyboard.Row(TelegramCopy.NAV_RECENT, TelegramCopy.NAV_SELECT_CHILD));
        String webAppUrl = deepLinkUrl(miniAppUrl);
        if (publicSiteUrl != null && !publicSiteUrl.isBlank()) {
            rows.add(new TelegramReplyKeyboard.Row(
                TelegramReplyKeyboard.Button.webApp(TelegramCopy.NAV_OPEN_APP, webAppUrl),
                new TelegramReplyKeyboard.Button(TelegramCopy.NAV_OPEN_SITE)));
        } else {
            rows.add(new TelegramReplyKeyboard.Row(
                TelegramReplyKeyboard.Button.webApp(TelegramCopy.NAV_OPEN_APP, webAppUrl)));
        }
        return TelegramReplyKeyboard.persistent(rows);
    }

    // EXPLAIN: Returns a reply keyboard for the child main view.
    // EXPLAIN: Two rows: [Мои задания | Награды] and [Последние | Открыть].
    public TelegramReplyKeyboard childMain() {
        var rows = java.util.List.of(
            new TelegramReplyKeyboard.Row(TelegramCopy.MY_TASKS, TelegramCopy.REWARDS),
            new TelegramReplyKeyboard.Row(
                new TelegramReplyKeyboard.Button(TelegramCopy.NAV_RECENT),
                TelegramReplyKeyboard.Button.webApp(TelegramCopy.OPEN_APP, deepLinkUrl(miniAppUrl)))
        );
        return TelegramReplyKeyboard.persistent(rows);
    }
}
