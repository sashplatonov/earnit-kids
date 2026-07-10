package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.request.FrequencyPeriod;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

final class FamilyActionFrequencyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter PERIOD_RESET_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final HistoryRepository historyRepository;
    private final TimeProvider timeProvider;

    FamilyActionFrequencyService(PurchaseRequestRepository purchaseRequestRepository,
                                 HistoryRepository historyRepository,
                                 TimeProvider timeProvider) {
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.historyRepository = historyRepository;
        this.timeProvider = timeProvider;
    }

    String validateTaskRequestLimit(int familyDbId, int childId, TaskEntity task) {
        Integer limit = extractFrequencyLimit(task.getFrequency());
        if (limit == null) {
            return null;
        }

        String period = extractFrequencyPeriod(task.getFrequency());
        Instant windowStart = currentPeriodStart(now(), period);
        Instant windowEnd = nextPeriodStart(windowStart, period);
        long usedCount = purchaseRequestRepository.countPendingTaskRequestsInWindow(
            familyDbId,
            childId,
            task.getTaskId(),
            windowStart,
            windowEnd
        ) + historyRepository.countTaskEarnsInWindow(
            familyDbId,
            childId,
            task.getTaskId(),
            windowStart,
            windowEnd
        );

        return usedCount >= limit ? BackendMessages.taskLimitReached(period, formatResetAt(windowEnd, period)) : null;
    }

    String validateItemRequestLimit(int familyDbId, int childId, ShopItemEntity item) {
        Integer limit = extractFrequencyLimit(item.getFrequency());
        if (limit == null) {
            return null;
        }

        String period = extractFrequencyPeriod(item.getFrequency());
        Instant windowStart = currentPeriodStart(now(), period);
        Instant windowEnd = nextPeriodStart(windowStart, period);
        long usedCount = purchaseRequestRepository.countPendingItemRequestsInWindow(
            familyDbId,
            childId,
            item.getItemId(),
            windowStart,
            windowEnd
        ) + historyRepository.countShopPurchasesInWindow(
            familyDbId,
            childId,
            item.getItemId(),
            windowStart,
            windowEnd
        );

        return usedCount >= limit ? BackendMessages.itemLimitReached(period, formatResetAt(windowEnd, period)) : null;
    }

    JsonNode buildFrequencyNode(Integer limit, FrequencyPeriod period) {
        if (limit == null || limit <= 0 || !isValidFrequencyPeriod(period)) {
            return null;
        }
        return OBJECT_MAPPER.createObjectNode()
            .put("limit", limit)
            .put("period", period.name());
    }

    private Integer extractFrequencyLimit(JsonNode rawFrequency) {
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

    private String extractFrequencyPeriod(JsonNode rawFrequency) {
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

    private boolean isValidFrequencyPeriod(FrequencyPeriod period) {
        return period != null;
    }

    private Instant currentPeriodStart(Instant currentInstant, String period) {
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

    private Instant nextPeriodStart(Instant currentPeriodStart, String period) {
        return switch (period) {
            case "week" -> currentPeriodStart.atZone(ZoneId.systemDefault())
                .plusWeeks(1)
                .toInstant();
            case "month" -> currentPeriodStart.atZone(ZoneId.systemDefault())
                .plusMonths(1)
                .toInstant();
            case "year" -> currentPeriodStart.atZone(ZoneId.systemDefault())
                .plusYears(1)
                .toInstant();
            case "season" -> currentPeriodStart.atZone(ZoneId.systemDefault())
                .plusMonths(3)
                .toInstant();
            default -> currentPeriodStart.atZone(ZoneId.systemDefault())
                .plusDays(1)
                .toInstant();
        };
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

    private String formatResetAt(Instant resetAt, String period) {
        var zonedResetAt = resetAt.atZone(ZoneId.systemDefault());
        if ("day".equals(period)) {
            return zonedResetAt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return zonedResetAt.format(PERIOD_RESET_FORMATTER);
    }

    private Instant now() {
        return timeProvider.now();
    }
}
