package com.sashplatonov.earnit.kids.family.application.analytics;

import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.TaskEntity;
import com.sashplatonov.earnit.kids.family.api.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryDailyAggregate;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryPeriodSummary;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRankedAggregate;
import com.sashplatonov.earnit.kids.util.ServiceResults;
import com.sashplatonov.earnit.kids.platform.application.observability.BackendKpiMetrics;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@ApplicationScoped
public class AnalyticsServiceImpl implements AnalyticsService {
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(60);

    private final Supplier<FamilyRepository> familyRepository;
    private final Supplier<HistoryRepository> historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final TimeProvider timeProvider;
    private final BackendKpiMetrics backendKpiMetrics;
    private final Duration analyticsCacheTtl;
    private final ConcurrentMap<String, AnalyticsCacheEntry> analyticsCache = new ConcurrentHashMap<>();

    @Inject
    public AnalyticsServiceImpl(FamilyRepository familyRepository,
                                HistoryRepository historyRepository,
                                TaskRepository taskRepository,
                                ShopItemRepository shopItemRepository,
                                TimeProvider timeProvider,
                                BackendKpiMetrics backendKpiMetrics,
                                @ConfigProperty(name = "app.performance.cache.analytics-ttl")
                                Duration analyticsCacheTtl) {
        this.familyRepository = () -> familyRepository;
        this.historyRepository = () -> historyRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
        this.timeProvider = timeProvider;
        this.backendKpiMetrics = backendKpiMetrics;
        this.analyticsCacheTtl = analyticsCacheTtl == null ? DEFAULT_CACHE_TTL : analyticsCacheTtl;
    }


    public AnalyticsServiceImpl(FamilyRepository familyRepository,
                                HistoryRepository historyRepository,
                                TaskRepository taskRepository,
                                ShopItemRepository shopItemRepository,
                                TimeProvider timeProvider,
                                BackendKpiMetrics backendKpiMetrics) {
        this(familyRepository, historyRepository, taskRepository, shopItemRepository, timeProvider,
            backendKpiMetrics, DEFAULT_CACHE_TTL);
    }

    @Override
    public OperationResult<AnalyticsResponse> getAnalyticsData(String familyId, Integer childId, String timeframe) {
        return backendKpiMetrics.recordResult("analytics", "get_data", () -> {
            AnalyticsTimeframe resolvedTimeframe = AnalyticsTimeframe.from(timeframe);
            String cacheKey = cacheKey(familyId, childId, resolvedTimeframe);
            AnalyticsCacheEntry cached = analyticsCache.get(cacheKey);
            if (cached != null && !isExpired(cached)) {
                return OperationResult.success(cached.payload());
            }

            Optional<Integer> familyDbIdOpt = familyRepository.get().getDbId(familyId);
            if (familyDbIdOpt.isEmpty()) {
                return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            int familyDbId = familyDbIdOpt.get();
            Instant now = timeProvider.now();
            AnalyticsResponse response = buildAnalyticsResponse(familyDbId, childId, resolvedTimeframe, now);
            analyticsCache.put(cacheKey, new AnalyticsCacheEntry(now, response));
            return OperationResult.success(response);
        });
    }

    @Override
    public void invalidateCache(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            analyticsCache.clear();
            return;
        }
        String prefix = familyId + "|";
        analyticsCache.keySet().removeIf(key -> key.startsWith(prefix));
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

    private AnalyticsResponse buildAnalyticsResponse(
        int familyDbId,
        Integer childId,
        AnalyticsTimeframe timeframe,
        Instant now
    ) {
        Duration periodDuration = timeframe.duration();
        Instant periodStart = now.minus(periodDuration);
        Instant previousStart = periodStart.minus(periodDuration);

        var currentRaw = historyRepository.get().summarizePeriod(familyDbId, childId, periodStart, now);
        var previousRaw = historyRepository.get().summarizePeriod(familyDbId, childId, previousStart, periodStart);

        HistoryPeriodSummary currentSummary = normalizeSummary(currentRaw);
        HistoryPeriodSummary previousSummary = normalizeSummary(previousRaw);

        var summary = new AnalyticsResponse.AnalyticsSummary(
            currentSummary.earned(),
            currentSummary.spent(),
            currentSummary.net()
        );
        var comparison = new AnalyticsResponse.AnalyticsSummary(
            previousSummary.earned(),
            previousSummary.spent(),
            previousSummary.net()
        );

        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        List<ShopItemEntity> items = queryShopItems(familyDbId, childId);

        List<AnalyticsResponse.AnalyticsStatItem> topTasks = buildTopTaskStatsAggregated(
            historyRepository.get().topTasksInPeriod(familyDbId, childId, periodStart, now),
            tasks
        );
        List<AnalyticsResponse.AnalyticsStatItem> topItems = buildTopItemStatsAggregated(
            historyRepository.get().topItemsInPeriod(familyDbId, childId, periodStart, now),
            items
        );
        List<AnalyticsResponse.AnalyticsTrendPoint> trends = buildTrendsAggregated(
            historyRepository.get().dailyTrendInPeriod(familyDbId, childId, periodStart, now)
        );
        List<AnalyticsResponse.AnalyticsRecommendation> recommendations = buildRecommendations(familyDbId, childId);

        return new AnalyticsResponse(summary, topTasks, topItems, trends, comparison, recommendations);
    }

    private HistoryPeriodSummary normalizeSummary(HistoryPeriodSummary summary) {
        return summary == null ? HistoryPeriodSummary.EMPTY : summary;
    }

    private List<AnalyticsResponse.AnalyticsStatItem> buildTopTaskStatsAggregated(
            List<HistoryRankedAggregate> aggregatedRows, List<TaskEntity> tasks) {
        Map<Long, String> namesByTaskId = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskEntity::getTaskId, TaskEntity::getName,
                (left, right) -> left));

        return aggregatedRows.stream()
            .map(row -> {
                int coins = Math.toIntExact(row.amount());
                int count = Math.toIntExact(row.count());
                String name = namesByTaskId.get(row.relatedId());
                if (name == null || name.isBlank()) {
                    name = BackendMessages.message("analytics.taskFallback");
                }
                return new AnalyticsResponse.AnalyticsStatItem(name, coins, count);
            })
            .toList();
    }

    private List<AnalyticsResponse.AnalyticsStatItem> buildTopItemStatsAggregated(
            List<HistoryRankedAggregate> aggregatedRows, List<ShopItemEntity> items) {
        Map<Long, String> namesByItemId = items.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemEntity::getItemId, ShopItemEntity::getName,
                (left, right) -> left));

        return aggregatedRows.stream()
            .map(row -> {
                int coins = Math.toIntExact(row.amount());
                int count = Math.toIntExact(row.count());
                String name = namesByItemId.get(row.relatedId());
                if (name == null || name.isBlank()) {
                    name = BackendMessages.message("analytics.itemFallback");
                }
                return new AnalyticsResponse.AnalyticsStatItem(name, coins, count);
            })
            .toList();
    }

    private List<AnalyticsResponse.AnalyticsTrendPoint> buildTrendsAggregated(
        List<HistoryDailyAggregate> dailyRows
    ) {
        var perDay = new LinkedHashMap<LocalDate, AnalyticsResponse.AnalyticsSummary>();
        for (var row : dailyRows) {
            LocalDate day = row.date();
            int amount = Math.toIntExact(row.amount());
            var agg = perDay.computeIfAbsent(day, unused -> new AnalyticsResponse.AnalyticsSummary(0, 0, 0));
            int earned = agg.totalEarned();
            int spent = agg.totalSpent();
            if (row.type() == HistoryEntryType.earn) {
                earned = Math.addExact(earned, amount);
            } else if (row.type() == HistoryEntryType.spend) {
                spent = Math.addExact(spent, amount);
            }
            perDay.put(day, new AnalyticsResponse.AnalyticsSummary(
                earned,
                spent,
                Math.subtractExact(earned, spent)
            ));
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
            return historyRepository.get().list(
                "familyId = ?1 AND childId = ?2 AND createdAt >= ?3 AND createdAt < ?4",
                familyDbId, childId, from, to);
        }

        return historyRepository.get().list("familyId = ?1 AND createdAt >= ?2 AND createdAt < ?3",
            familyDbId, from, to);
    }

    private boolean isExpired(AnalyticsCacheEntry cached) {
        if (analyticsCacheTtl.isZero() || analyticsCacheTtl.isNegative()) {
            return true;
        }
        return Duration.between(cached.cachedAt(), timeProvider.now()).compareTo(analyticsCacheTtl) >= 0;
    }

    private String cacheKey(String familyId, Integer childId, AnalyticsTimeframe timeframe) {
        return familyId + "|" + String.valueOf(childId) + "|" + timeframe.cacheKey();
    }
}
