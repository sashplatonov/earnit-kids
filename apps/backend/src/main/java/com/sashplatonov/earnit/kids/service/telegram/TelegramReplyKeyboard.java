package com.sashplatonov.earnit.kids.service.telegram;

public record TelegramReplyKeyboard(
    java.util.List<Row> rows,
    boolean isPersistent,
    boolean resizeKeyboard,
    boolean oneTimeKeyboard
) {
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

        public static Button webApp(String label, String url) {
            return new Button(label, url);
        }
    }

    public static TelegramReplyKeyboard persistent(java.util.List<Row> rows) {
        return new TelegramReplyKeyboard(rows, true, true, false);
    }
}
