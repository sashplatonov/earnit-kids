package com.sashplatonov.earnit.kids.service.telegram;

import java.util.OptionalLong;

final class TelegramActionIdParser {
    private TelegramActionIdParser() {
    }

    static OptionalLong parse(String data, String prefix) {
        try {
            long value = Long.parseLong(data.substring(prefix.length()));
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }
}
