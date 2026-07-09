package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final FamilyRepository familyRepository;
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final TimeProvider timeProvider;

    @Override
    public OperationResult<AnalyticsResponse> getAnalyticsData(String familyId, Integer childId, String timeframe) {
        Optional<Integer> familyDbIdOpt = familyRepository.getDbId(familyId);
        if (familyDbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = familyDbIdOpt.get();
        Duration periodDuration = resolveTimeframeDuration(timeframe);
        Instant now = timeProvider.now();
        Instant periodStart = now.minus(periodDuration);
        Instant previousStart = periodStart.minus(periodDuration);

        // EXPLAIN: Use SQL aggregation instead of loading full history rows.
        var currentRaw = historyRepository.summarizePeriod(familyDbId, childId, periodStart, now);
        var previousRaw = historyRepository.summarizePeriod(familyDbId, childId, previousStart, periodStart);

        int[] currentSummary = normalizeSummary(currentRaw);
        int[] previousSummary = normalizeSummary(previousRaw);

        int currentNet = currentSummary[0] - currentSummary[1];
        int previousNet = previousSummary[0] - previousSummary[1];

        var summary = new AnalyticsResponse.AnalyticsSummary(currentSummary[0], currentSummary[1], currentNet);
        var comparison = new AnalyticsResponse.AnalyticsSummary(previousSummary[0], previousSummary[1], previousNet);

        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        List<ShopItemEntity> items = queryShopItems(familyDbId, childId);

        List<AnalyticsResponse.AnalyticsStatItem> topTasks = buildTopTaskStatsAggregated(
            historyRepository.topTasksInPeriod(familyDbId, childId, periodStart, now), tasks);
        List<AnalyticsResponse.AnalyticsStatItem> topItems = buildTopItemStatsAggregated(
            historyRepository.topItemsInPeriod(familyDbId, childId, periodStart, now), items);

        List<AnalyticsResponse.AnalyticsTrendPoint> trends = buildTrendsAggregated(
            historyRepository.dailyTrendInPeriod(familyDbId, childId, periodStart, now));

        List<AnalyticsResponse.AnalyticsRecommendation> recommendations = buildRecommendations(familyDbId, childId);

        return OperationResult.success(new AnalyticsResponse(
            summary,
            topTasks,
            topItems,
            trends,
            comparison,
            recommendations
        ));
    }

    private List<TaskEntity> queryTasks(int familyDbId, Integer childId) {
        if (childId != null) {
            return taskRepository.list("familyId = ?1 AND childId = ?2 AND deleted = false", familyDbId, childId);
        }
        return taskRepository.list("familyId = ?1 AND deleted = false", familyDbId);
    }

    private List<ShopItemEntity> queryShopItems(int familyDbId, Integer childId) {
        if (childId != null) {
            return shopItemRepository.list("familyId = ?1 AND childId = ?2 AND deleted = false", familyDbId, childId);
        }
        return shopItemRepository.list("familyId = ?1 AND deleted = false", familyDbId);
    }

    private Duration resolveTimeframeDuration(String timeframe) {
        if ("week".equalsIgnoreCase(timeframe)) {
            return Duration.ofDays(7);
        }
        if ("year".equalsIgnoreCase(timeframe)) {
            return Duration.ofDays(365);
        }
        return Duration.ofDays(30);
    }

    private int[] normalizeSummary(int[] summary) {
        if (summary == null || summary.length < 2) {
            return new int[]{0, 0};
        }
        return summary;
    }

    // EXPLAIN: Build top task stats from SQL-aggregated rows: [relatedId, sumAmount, count].
    private List<AnalyticsResponse.AnalyticsStatItem> buildTopTaskStatsAggregated(
            List<Object[]> aggregatedRows, List<TaskEntity> tasks) {
        Map<Long, String> namesByTaskId = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskEntity::getTaskId, TaskEntity::getName,
                (left, right) -> left));

        return aggregatedRows.stream()
            .map(row -> {
                Long relatedId = row[0] instanceof Number n ? n.longValue() : null;
                int coins = ((Number) row[1]).intValue();
                int count = ((Number) row[2]).intValue();
                String name = relatedId != null ? namesByTaskId.get(relatedId) : null;
                if (name == null || name.isBlank()) {
                    name = BackendMessages.message("analytics.taskFallback");
                }
                return new AnalyticsResponse.AnalyticsStatItem(name, coins, count);
            })
            .toList();
    }

    // EXPLAIN: Build top item stats from SQL-aggregated rows: [relatedId, sumAmount, count].
    private List<AnalyticsResponse.AnalyticsStatItem> buildTopItemStatsAggregated(
            List<Object[]> aggregatedRows, List<ShopItemEntity> items) {
        Map<Long, String> namesByItemId = items.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemEntity::getItemId, ShopItemEntity::getName,
                (left, right) -> left));

        return aggregatedRows.stream()
            .map(row -> {
                Long relatedId = row[0] instanceof Number n ? n.longValue() : null;
                int coins = ((Number) row[1]).intValue();
                int count = ((Number) row[2]).intValue();
                String name = relatedId != null ? namesByItemId.get(relatedId) : null;
                if (name == null || name.isBlank()) {
                    name = BackendMessages.message("analytics.itemFallback");
                }
                return new AnalyticsResponse.AnalyticsStatItem(name, coins, count);
            })
            .toList();
    }

    // EXPLAIN: Build daily trend from SQL-aggregated rows: [date, type, sumAmount].
    private List<AnalyticsResponse.AnalyticsTrendPoint> buildTrendsAggregated(List<Object[]> dailyRows) {
        var perDay = new LinkedHashMap<LocalDate, AnalyticsResponse.AnalyticsSummary>();
        for (var row : dailyRows) {
            LocalDate day = row[0] instanceof java.sql.Date d ? d.toLocalDate()
                : row[0] instanceof LocalDate ld ? ld : null;
            if (day == null) {
                continue;
            }
            String type = (String) row[1];
            int amount = ((Number) row[2]).intValue();
            var agg = perDay.computeIfAbsent(day, unused -> new AnalyticsResponse.AnalyticsSummary(0, 0, 0));
            if ("earn".equals(type)) {
                var earnedSummary = new AnalyticsResponse.AnalyticsSummary(amount, agg.totalSpent(), amount);
                perDay.put(day, earnedSummary);
            } else if ("spend".equals(type)) {
                int net = agg.totalEarned() - amount;
                perDay.put(day, new AnalyticsResponse.AnalyticsSummary(agg.totalEarned(), amount, net));
            }
        }

        return perDay.entrySet().stream()
            .map(entry -> new AnalyticsResponse.AnalyticsTrendPoint(
                entry.getKey().toString(),
                entry.getValue().totalEarned(),
                entry.getValue().totalSpent()))
            .toList();
    }

    private List<AnalyticsResponse.AnalyticsRecommendation> buildRecommendations(int familyDbId, Integer childId) {
        Instant now = timeProvider.now();
        Instant lastMonthStart = now.minus(Duration.ofDays(30));
        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<HistoryEntryEntity> monthlyHistory = queryHistory(familyDbId, childId, lastMonthStart, now)
            .stream()
            .filter(entry -> entry.getType() == HistoryEntryType.earn)
            .toList();

        Map<Long, Integer> completionCounts = new LinkedHashMap<>();
        for (HistoryEntryEntity entry : monthlyHistory) {
            if (entry.getRelatedId() == null) {
                continue;
            }
            int nextCount = completionCounts.getOrDefault(entry.getRelatedId(), 0) + 1;
            completionCounts.put(entry.getRelatedId(), nextCount);
        }

        return tasks.stream()
            .sorted(Comparator
                .comparingInt((TaskEntity task) -> completionCounts.getOrDefault(task.getTaskId(), 0))
                .thenComparing(TaskEntity::getCoins, Comparator.reverseOrder()))
            .limit(3)
            .map(task -> new AnalyticsResponse.AnalyticsRecommendation(
                task.getName(),
                task.getCoins(),
                completionCounts.getOrDefault(task.getTaskId(), 0) == 0
                    ? BackendMessages.message("analytics.recommendationStale")
                    : BackendMessages.message("analytics.recommendationRepeat")
            ))
            .toList();
    }

    private List<HistoryEntryEntity> queryHistory(int familyDbId, Integer childId, Instant from, Instant to) {
        if (childId != null) {
            return historyRepository.list(
                "familyId = ?1 AND childId = ?2 AND createdAt >= ?3 AND createdAt < ?4",
                familyDbId, childId, from, to);
        }

        return historyRepository.list("familyId = ?1 AND createdAt >= ?2 AND createdAt < ?3",
            familyDbId, from, to);
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }
}
