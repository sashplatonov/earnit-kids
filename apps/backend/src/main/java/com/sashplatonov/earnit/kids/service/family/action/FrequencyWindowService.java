package com.sashplatonov.earnit.kids.service.family.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.request.FrequencyPeriod;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

final class FrequencyWindowService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter PERIOD_RESET_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    Integer extractFrequencyLimit(JsonNode rawFrequency) {
        JsonNode frequency = normalizeFrequency(rawFrequency);
        if (frequency == null || !frequency.isObject()) {
            return null;
        }

        JsonNode limitNode = frequency.get("limit");
        if (limitNode == null || !limitNode.canConvertToInt()) {
            return null;
        }

        int limit = limitNode.asInt();
        return limit > 0 ? limit : null;
    }

    String extractFrequencyPeriod(JsonNode rawFrequency) {
        JsonNode frequency = normalizeFrequency(rawFrequency);
        if (frequency == null || !frequency.isObject()) {
            return "day";
        }

        String period = Optional.ofNullable(frequency.get("period"))
            .map(JsonNode::asText)
            .map(String::trim)
            .orElse("day");

        return switch (period) {
            case "week", "month", "year", "season" -> period;
            default -> "day";
        };
    }

    boolean isValidFrequencyPeriod(FrequencyPeriod period) {
        return period != null;
    }

    Instant currentPeriodStart(Instant currentInstant, String period) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate currentDate = currentInstant.atZone(zoneId).toLocalDate();

        return switch (period) {
            case "week" -> currentDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zoneId)
                .toInstant();
            case "month" -> currentDate.withDayOfMonth(1).atStartOfDay(zoneId).toInstant();
            case "year" -> currentDate.withDayOfYear(1).atStartOfDay(zoneId).toInstant();
            case "season" -> currentSeasonStart(currentDate, zoneId);
            default -> currentDate.atStartOfDay(zoneId).toInstant();
        };
    }

    Instant nextPeriodStart(Instant currentPeriodStart, String period) {
        return switch (period) {
            case "week" -> currentPeriodStart.atZone(ZoneId.systemDefault()).plusWeeks(1).toInstant();
            case "month" -> currentPeriodStart.atZone(ZoneId.systemDefault()).plusMonths(1).toInstant();
            case "year" -> currentPeriodStart.atZone(ZoneId.systemDefault()).plusYears(1).toInstant();
            case "season" -> currentPeriodStart.atZone(ZoneId.systemDefault()).plusMonths(3).toInstant();
            default -> currentPeriodStart.atZone(ZoneId.systemDefault()).plusDays(1).toInstant();
        };
    }

    String formatResetAt(Instant resetAt, String period) {
        var zonedResetAt = resetAt.atZone(ZoneId.systemDefault());
        if ("day".equals(period)) {
            return zonedResetAt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return zonedResetAt.format(PERIOD_RESET_FORMATTER);
    }

    private JsonNode normalizeFrequency(JsonNode rawFrequency) {
        if (rawFrequency == null || rawFrequency.isNull()) {
            return null;
        }
        if (rawFrequency.isObject()) {
            return rawFrequency;
        }
        if (!rawFrequency.isTextual()) {
            return null;
        }

        String value = rawFrequency.asText();
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant currentSeasonStart(LocalDate currentDate, ZoneId zoneId) {
        int month = currentDate.getMonthValue();
        LocalDate seasonStart = switch (month) {
            case 12 -> LocalDate.of(currentDate.getYear(), 12, 1);
            case 1, 2 -> LocalDate.of(currentDate.getYear() - 1, 12, 1);
            case 3, 4, 5 -> LocalDate.of(currentDate.getYear(), 3, 1);
            case 6, 7, 8 -> LocalDate.of(currentDate.getYear(), 6, 1);
            default -> LocalDate.of(currentDate.getYear(), 9, 1);
        };
        return seasonStart.atStartOfDay(zoneId).toInstant();
    }
}
