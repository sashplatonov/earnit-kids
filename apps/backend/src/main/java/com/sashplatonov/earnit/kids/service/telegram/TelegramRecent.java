package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

// EXPLAIN: Builds the Recent preview message body. Renders at most five
// EXPLAIN: presentation-safe rows with human-readable titles and relative
// EXPLAIN: dates, so the bot never becomes a full history browser (BUX-005).
final class TelegramRecent {
    private static final String[] MONTHS = {
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    private TelegramRecent() {
    }

    static String format(TelegramQuickActionResponse view, Instant now) {
        String header = TelegramBotEmoji.RECENT + " Последние события · " + view.childName();
        List<HistoryEntryDto> history = view.history();
        if (history.isEmpty()) {
            return header + "\n\n" + TelegramCopy.emptyRecent();
        }
        StringBuilder builder = new StringBuilder(header);
        history.stream().limit(5).forEach(entry -> builder.append("\n\n").append(row(entry, now)));
        return builder.toString();
    }

    static String row(HistoryEntryDto entry, Instant now) {
        return emoji(entry) + " " + amount(entry) + " · " + title(entry) + "\n" + formatDate(entry.createdAt(), now);
    }

    private static String emoji(HistoryEntryDto entry) {
        return entry.type() == HistoryEntryType.spend ? TelegramBotEmoji.REWARDS : TelegramBotEmoji.TASK_DONE;
    }

    private static String amount(HistoryEntryDto entry) {
        return entry.amount() >= 0 ? "+" + entry.amount() : Integer.toString(entry.amount());
    }

    private static String title(HistoryEntryDto entry) {
        return entry.title() != null ? entry.title()
            : entry.taskName() != null ? entry.taskName()
            : entry.itemName() != null ? entry.itemName() : "Событие";
    }

    // EXPLAIN: Deterministic UTC-relative label: Сегодня / Вчера / absolute date.
    private static String formatDate(String createdAt, Instant now) {
        try {
            LocalDateTime time = LocalDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC);
            LocalDate day = time.toLocalDate();
            LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
            String clock = String.format("%02d:%02d", time.getHour(), time.getMinute());
            if (day.equals(today)) {
                return "Сегодня, " + clock;
            }
            if (day.equals(today.minusDays(1))) {
                return "Вчера, " + clock;
            }
            return time.getDayOfMonth() + " " + MONTHS[time.getMonthValue() - 1] + ", " + clock;
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
