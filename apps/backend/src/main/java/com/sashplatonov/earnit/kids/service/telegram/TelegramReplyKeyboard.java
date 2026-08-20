package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Immutable description of a Telegram ReplyKeyboardMarkup.
// EXPLAIN: Carries is_persistent, resize_keyboard, and one_time_keyboard
// EXPLAIN: per the persistent-keyboard UX spec (UX-01).
public record TelegramReplyKeyboard(
    java.util.List<Row> rows,
    boolean isPersistent,
    boolean resizeKeyboard,
    boolean oneTimeKeyboard
) {
    // EXPLAIN: Canonical constructor makes defensive copies to prevent external mutation.
    public TelegramReplyKeyboard(java.util.List<Row> rows, boolean isPersistent,
            boolean resizeKeyboard, boolean oneTimeKeyboard) {
        this.rows = java.util.List.copyOf(rows);
        this.isPersistent = isPersistent;
        this.resizeKeyboard = resizeKeyboard;
        this.oneTimeKeyboard = oneTimeKeyboard;
    }

    public TelegramReplyKeyboard(java.util.List<Row> rows) {
        this(rows, true, true, false);
    }

    @Override
    public java.util.List<Row> rows() {
        return java.util.List.copyOf(rows);
    }

    public record Row(java.util.List<Button> buttons) {
        // EXPLAIN: Canonical constructor makes a defensive copy of the buttons list.
        public Row(java.util.List<Button> buttons) {
            this.buttons = java.util.List.copyOf(buttons);
        }

        public Row(String... labels) {
            this(java.util.Arrays.stream(labels).map(Button::new).toList());
        }

        public Row(Button... buttons) {
            this(java.util.List.of(buttons));
        }

        public java.util.List<Button> buttons() {
            return java.util.List.copyOf(buttons);
        }
    }

    public record Button(String label, String webAppUrl) {
        public Button(String label) {
            this(label, null);
        }

        // EXPLAIN: A KeyboardButton with web_app capability opens the Mini App
        // EXPLAIN: directly from the persistent keyboard (UX-04). Telegram
        // EXPLAIN: KeyboardButton has no `url` field — only web_app (WebAppInfo).
        public static Button webApp(String label, String url) {
            return new Button(label, url);
        }
    }

    // EXPLAIN: Convenience factory matching the persistent-keyboard UX spec.
    public static TelegramReplyKeyboard persistent(java.util.List<Row> rows) {
        return new TelegramReplyKeyboard(rows, true, true, false);
    }
}
