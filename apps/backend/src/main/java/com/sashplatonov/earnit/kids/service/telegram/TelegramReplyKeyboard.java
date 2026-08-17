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

    // EXPLAIN: Convenience constructor defaults to persistent keyboard settings.
    public TelegramReplyKeyboard(java.util.List<Row> rows) {
        this(rows, true, true, false);
    }

    // EXPLAIN: Accessor returns an unmodifiable view of rows.
    @Override
    public java.util.List<Row> rows() {
        return java.util.List.copyOf(rows);
    }

    public record Row(java.util.List<Button> buttons) {
        // EXPLAIN: Canonical constructor makes a defensive copy of the buttons list.
        public Row(java.util.List<Button> buttons) {
            this.buttons = java.util.List.copyOf(buttons);
        }

        // EXPLAIN: Convenience constructor accepting label strings.
        public Row(String... labels) {
            this(java.util.Arrays.stream(labels).map(Button::new).toList());
        }

        // EXPLAIN: Convenience constructor accepting pre-built buttons (e.g. a
        // EXPLAIN: web_app button that opens the Mini App client-side).
        public Row(Button... buttons) {
            this(java.util.List.of(buttons));
        }

        // EXPLAIN: Accessor returns an unmodifiable view of buttons.
        public java.util.List<Button> buttons() {
            return java.util.List.copyOf(buttons);
        }
    }

    public record Button(String label, String url) {
        public Button(String label) {
            this(label, null);
        }

        // EXPLAIN: A KeyboardButton with url field opens the URL directly in the
        // EXPLAIN: in-app browser when tapped (no message sent to the bot).
        // EXPLAIN: UX-04 — preferred over web_app for reply keyboards.
        public static Button url(String label, String url) {
            return new Button(label, url);
        }
    }

    // EXPLAIN: Convenience factory matching the persistent-keyboard UX spec.
    public static TelegramReplyKeyboard persistent(java.util.List<Row> rows) {
        return new TelegramReplyKeyboard(rows, true, true, false);
    }
}
