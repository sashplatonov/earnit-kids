package com.sashplatonov.earnit.kids.telegram.application.callback;

import java.util.OptionalLong;

public final class TelegramActionIdParser {
    private TelegramActionIdParser() {
    }

    public static OptionalLong parse(String data, String prefix) {
        try {
            long value = Long.parseLong(data.substring(prefix.length()));
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }
}
