package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.api.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

final class TelegramRecent {
    private static final TelegramMessageResolver MESSAGES = new TelegramMessageResolver();
    private TelegramRecent() {
    }

    static String format(TelegramQuickActionResponse view, Instant now) {
        FamilyLocale locale = view.locale();
        String header = MESSAGES.text(locale, "telegram.recent.header",
            java.util.Map.of("child", view.childName()));
        List<HistoryEntryDto> history = view.history();
        if (history.isEmpty()) {
            return header + "\n\n" + MESSAGES.text(locale, "telegram.outcome.noRecent");
        }
        StringBuilder builder = new StringBuilder(header);
        history.stream().limit(5).forEach(entry -> builder.append("\n\n").append(row(entry, now, locale)));
        return builder.toString();
    }

    static String row(HistoryEntryDto entry, Instant now) {
        return row(entry, now, TelegramLocaleContext.current());
    }

    private static String row(HistoryEntryDto entry, Instant now, FamilyLocale locale) {
        boolean earning = entry.type() != HistoryEntryType.spend;
        return historyDelta(entry.amount(), earning) + " · " + title(entry, locale)
            + "\n" + formatDate(entry.createdAt(), now, locale);
    }

    private static String historyDelta(int amount, boolean earning) {
        int absoluteAmount = Math.abs(amount);
        String marker = earning ? TelegramBotEmoji.COINS_EARNED : TelegramBotEmoji.COINS_SPENT;
        String sign = earning ? "+" : "-";
        return marker + " " + sign + absoluteAmount + TelegramBotEmoji.COINS;
    }

    private static String title(HistoryEntryDto entry, FamilyLocale locale) {
        return entry.title() != null ? entry.title()
            : entry.taskName() != null ? entry.taskName()
            : entry.itemName() != null ? entry.itemName()
            : MESSAGES.text(locale, "telegram.recent.event");
    }

    private static String formatDate(String createdAt, Instant now, FamilyLocale locale) {
        try {
            LocalDateTime time = LocalDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC);
            LocalDate day = time.toLocalDate();
            LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
            String clock = String.format("%02d:%02d", time.getHour(), time.getMinute());
            if (day.equals(today)) {
                return MESSAGES.text(locale, "telegram.recent.today",
                    java.util.Map.of("time", clock));
            }
            if (day.equals(today.minusDays(1))) {
                return MESSAGES.text(locale, "telegram.recent.yesterday",
                    java.util.Map.of("time", clock));
            }
            String month = MESSAGES.text(locale,
                "telegram.recent.month." + time.getMonthValue());
            return MESSAGES.text(locale, "telegram.recent.date",
                java.util.Map.of("day", time.getDayOfMonth(), "month", month, "time", clock));
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
