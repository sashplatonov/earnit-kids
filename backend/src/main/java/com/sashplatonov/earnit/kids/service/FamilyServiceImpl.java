package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.FamilyService;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public final class FamilyServiceImpl implements FamilyService {
    private static final Logger LOG = Logger.getLogger(FamilyServiceImpl.class);
    private static final Set<String> VALID_THEMES = Set.of("mint", "ocean", "sun", "coral", "cosmos");

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FamilyDataRepository familyDataRepository;
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;

    @Inject
    public FamilyServiceImpl(FamilyRepository familyRepository,
                             ChildRepository childRepository,
                             FamilyDataRepository familyDataRepository,
                             HistoryRepository historyRepository,
                             TaskRepository taskRepository,
                             ShopItemRepository shopItemRepository) {
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.familyDataRepository = familyDataRepository;
        this.historyRepository = historyRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
    }

    @Override
    public OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        int familyDbId = dbIdOpt.get();

        List<ChildEntity> children = childRepository.getChildren(familyDbId);
        if (children.isEmpty()) {
            return OperationResult.success(new FamilyDataResponse(
                0, List.of(), List.of(), List.of(), List.of(), List.of(),
                true, List.of(), null, null, null, null));
        }

        ChildEntity activeChild = childId != null
            ? children.stream().filter(c -> c.getId() == childId).findFirst().orElse(children.getFirst())
            : children.getFirst();

        List<TaskDto> tasks = familyDataRepository.getTasks(activeChild.getId()).stream()
            .map(t -> new TaskDto(t.getTaskId(), t.getName(), t.getCoins(), t.getGroupName(),
                t.getFrequency(), t.getComment(), t.getMoneyLimit(), t.getChildId()))
            .toList();

        List<ShopItemDto> shopItems = familyDataRepository.getShopItems(activeChild.getId()).stream()
            .map(s -> new ShopItemDto(s.getItemId(), s.getName(), s.getPrice(), s.getGroupName(),
                s.getFrequency(), s.getMoneyLimit(), s.getChildId()))
            .toList();

        List<HistoryEntryDto> history = familyDataRepository.getHistory(activeChild.getId(), 50, 0).stream()
            .map(this::toHistoryDto)
            .toList();

        List<RequestDto> requests = familyDataRepository.getRequests(familyDbId, 50, 0).stream()
            .map(this::toRequestDto)
            .toList();

        List<FriendDto> friends = familyDataRepository.getFriendChildIds(activeChild.getId()).stream()
            .map(fid -> childRepository.findByIdOptional(fid).orElse(null))
            .filter(java.util.Objects::nonNull)
            .map(f -> new FriendDto(f.getId(), f.getName(), f.getBalance()))
            .toList();

        List<ChildDto> childDtos = children.stream()
            .map(c -> new ChildDto(c.getId(), c.getName(), c.getBalance(),
                c.getMonthlyLimit(), c.getDailyCoinLimit(), c.getTheme()))
            .toList();

        return OperationResult.success(
            new FamilyDataResponse(activeChild.getBalance(), tasks, shopItems, history, requests,
                friends, true, childDtos, null, activeChild.getName(),
                activeChild.getMonthlyLimit(), activeChild.getDailyCoinLimit()));
    }

    @Override
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                               Map<String, Object> payload) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        return loadFamilyData(familyId, childId);
    }

    @Override
    public OperationResult<ChildInfo> createChild(String familyId, String childName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        int familyDbId = dbIdOpt.get();

        if (childName == null || childName.isBlank()) {
            return OperationResult.failure("Имя ребенка обязательно");
        }
        if (childName.length() > 50) {
            return OperationResult.failure("Имя слишком длинное");
        }
        if (childRepository.isNicknameTaken(familyDbId, childName, null)) {
            return OperationResult.failure("Это имя уже занято");
        }

        Optional<ChildEntity> childOpt = childRepository.createChild(familyDbId, childName);
        if (childOpt.isEmpty()) {
            return OperationResult.failure("Ошибка создания");
        }

        ChildEntity child = childOpt.get();
        return OperationResult.success(new ChildInfo(child.getId(), child.getName(), child.getToken()));
    }

    @Override
    public OperationResult<Void> deleteChild(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        var childOpt = childRepository.findByIdOptional(childId);
        if (childOpt.isEmpty() || childOpt.get().getFamilyDbId() != dbIdOpt.get()) {
            return OperationResult.failure("Ребенок не найден");
        }

        childRepository.deleteChild(childId);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateNickname(String familyId, int childId, String newName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        if (newName == null || newName.isBlank()) {
            return OperationResult.failure("Имя обязательно");
        }
        if (childRepository.isNicknameTaken(dbIdOpt.get(), newName, childId)) {
            return OperationResult.failure("Это имя уже занято");
        }

        childRepository.updateName(childId, newName);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildSettings(String familyId, int childId,
                                                      String name, int dailyCoinLimit,
                                                      int monthlyLimit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        childRepository.updateSettings(childId, name, dailyCoinLimit, monthlyLimit);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildTheme(int childId, String theme) {
        if (!VALID_THEMES.contains(theme)) {
            return OperationResult.failure("Недопустимая тема: " + theme);
        }
        childRepository.updateTheme(childId, theme);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<List<FriendDto>> searchByNickname(String nickname, int excludeChildId) {
        if (nickname == null || nickname.isBlank() || nickname.trim().length() < 3) {
            return OperationResult.success(List.of());
        }

        List<FriendDto> results = childRepository.searchByNickname(nickname.trim(), excludeChildId).stream()
            .map(child -> new FriendDto(child.getId(), child.getName(), child.getBalance()))
            .toList();

        return OperationResult.success(results);
    }

    @Override
    public OperationResult<Void> addFriend(String familyId, int childId, int friendChildId) {
        if (childId == friendChildId) {
            return OperationResult.failure("Cannot add yourself");
        }

        if (familyRepository.getDbId(familyId).isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        if (childRepository.findByIdOptional(friendChildId).isEmpty()) {
            return OperationResult.failure("User not found");
        }

        boolean saved = familyDataRepository.addFriend(childId, friendChildId);
        if (!saved) {
            return OperationResult.failure("Already friends or failed to add");
        }

        return OperationResult.success(null);
    }

    @Override
    public OperationResult<List<FriendDto>> getFriendsData(int childId) {
        List<FriendDto> friends = familyDataRepository.getFriendChildIds(childId).stream()
            .map(fid -> childRepository.findByIdOptional(fid).orElse(null))
            .filter(java.util.Objects::nonNull)
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();

        return OperationResult.success(friends);
    }

    @Override
    public OperationResult<Map<String, Object>> getAnalyticsData(String familyId, Integer childId, String timeframe) {
        Optional<Integer> familyDbIdOpt = familyRepository.getDbId(familyId);
        if (familyDbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        int familyDbId = familyDbIdOpt.get();
        Duration periodDuration = resolveTimeframeDuration(timeframe);
        Instant now = Instant.now();
        Instant periodStart = now.minus(periodDuration);
        Instant previousStart = periodStart.minus(periodDuration);

        List<HistoryEntryEntity> currentPeriodHistory = queryHistory(familyDbId, childId, periodStart, now);
        List<HistoryEntryEntity> previousPeriodHistory = queryHistory(familyDbId, childId, previousStart, periodStart);
        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        List<ShopItemEntity> items = queryShopItems(familyDbId, childId);

        Map<String, Integer> summary = summarize(currentPeriodHistory);
        Map<String, Integer> comparison = summarize(previousPeriodHistory);
        List<Map<String, Object>> topTasks = buildTopTaskStats(currentPeriodHistory, tasks);
        List<Map<String, Object>> topItems = buildTopItemStats(currentPeriodHistory, items);
        List<Map<String, Object>> trends = buildTrends(currentPeriodHistory);
        List<Map<String, Object>> recommendations = buildRecommendations(familyDbId, childId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("topTasks", topTasks);
        payload.put("topItems", topItems);
        payload.put("trends", trends);
        payload.put("comparison", comparison);
        payload.put("recommendations", recommendations);
        return OperationResult.success(payload);
    }

    @Override
    public OperationResult<PaginatedHistory> getHistory(int childId, int page, int limit) {
        int offset = (page - 1) * limit;
        List<HistoryEntryEntity> rows = familyDataRepository.getHistory(childId, limit, offset);
        int total = familyDataRepository.getHistoryCount(childId);
        List<HistoryEntryDto> items = rows.stream().map(this::toHistoryDto).toList();
        return OperationResult.success(new PaginatedHistory(items, total, page, limit));
    }

    @Override
    public OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        int familyDbId = dbIdOpt.get();
        int offset = (page - 1) * limit;
        List<PurchaseRequestEntity> rows = familyDataRepository.getRequests(familyDbId, limit, offset);
        int total = familyDataRepository.getRequestsCount(familyDbId);
        List<RequestDto> items = rows.stream().map(this::toRequestDto).toList();
        return OperationResult.success(new PaginatedRequests(items, total, page, limit));
    }

    @Override
    public OperationResult<String> getChildLoginLink(int childId) {
        var childOpt = childRepository.findByIdOptional(childId);
        if (childOpt.isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }
        return OperationResult.success(childOpt.get().getToken());
    }

    @Override
    public OperationResult<String> regenerateChildToken(int childId) {
        Optional<String> newToken = childRepository.regenerateToken(childId);
        if (newToken.isEmpty()) {
            return OperationResult.failure("Ошибка генерации токена");
        }
        return OperationResult.success(newToken.get());
    }

    @Override
    public OperationResult<Void> updatePreference(String familyId, String key, Object value) {
        if ("lastSelectedChildId".equals(key)) {
            Integer childId = value instanceof Number n ? n.intValue() : null;
            familyRepository.updateLastSelectedChild(familyId, childId);
            return OperationResult.success(null);
        }
        return OperationResult.failure("Неизвестная настройка: " + key);
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

    private Map<String, Integer> summarize(List<HistoryEntryEntity> historyEntries) {
        int totalEarned = historyEntries.stream()
            .filter(entry -> "earn".equals(entry.getType()))
            .mapToInt(HistoryEntryEntity::getAmount)
            .sum();
        int totalSpent = historyEntries.stream()
            .filter(entry -> "spend".equals(entry.getType()))
            .mapToInt(HistoryEntryEntity::getAmount)
            .sum();

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("totalEarned", totalEarned);
        summary.put("totalSpent", totalSpent);
        summary.put("netChange", totalEarned - totalSpent);
        return summary;
    }

    private List<Map<String, Object>> buildTopTaskStats(List<HistoryEntryEntity> historyEntries, List<TaskEntity> tasks) {
        Map<Long, String> namesByTaskId = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskEntity::getTaskId, TaskEntity::getName,
                (left, right) -> left));

        Map<String, Aggregate> byName = new LinkedHashMap<>();
        historyEntries.stream()
            .filter(entry -> "earn".equals(entry.getType()))
            .forEach(entry -> {
                String name = null;
                if (entry.getRelatedId() != null) {
                    name = namesByTaskId.get(entry.getRelatedId());
                }
                if (name == null || name.isBlank()) {
                    name = entry.getDescription() == null || entry.getDescription().isBlank()
                        ? "Задание"
                        : entry.getDescription();
                }

                Aggregate aggregate = byName.computeIfAbsent(name, unused -> new Aggregate());
                aggregate.coins += entry.getAmount();
                aggregate.count += 1;
            });

        return toTopStats(byName);
    }

    private List<Map<String, Object>> buildTopItemStats(List<HistoryEntryEntity> historyEntries,
                                                        List<ShopItemEntity> items) {
        Map<Long, String> namesByItemId = items.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemEntity::getItemId, ShopItemEntity::getName,
                (left, right) -> left));

        Map<String, Aggregate> byName = new LinkedHashMap<>();
        historyEntries.stream()
            .filter(entry -> "spend".equals(entry.getType()))
            .forEach(entry -> {
                String name = null;
                if (entry.getRelatedId() != null) {
                    name = namesByItemId.get(entry.getRelatedId());
                }
                if (name == null || name.isBlank()) {
                    name = entry.getDescription() == null || entry.getDescription().isBlank()
                        ? "Товар"
                        : entry.getDescription();
                }

                Aggregate aggregate = byName.computeIfAbsent(name, unused -> new Aggregate());
                aggregate.coins += entry.getAmount();
                aggregate.count += 1;
            });

        return toTopStats(byName);
    }

    private List<Map<String, Object>> toTopStats(Map<String, Aggregate> byName) {
        return byName.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, Aggregate> entry) -> entry.getValue().coins)
                .reversed())
            .map(entry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getKey());
                item.put("coins", entry.getValue().coins);
                item.put("count", entry.getValue().count);
                return item;
            })
            .toList();
    }

    private List<Map<String, Object>> buildTrends(List<HistoryEntryEntity> historyEntries) {
        Map<LocalDate, Aggregate> perDay = new LinkedHashMap<>();
        historyEntries.stream()
            .sorted(Comparator.comparing(HistoryEntryEntity::getCreatedAt))
            .forEach(entry -> {
                if (entry.getCreatedAt() == null) {
                    return;
                }
                LocalDate day = entry.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
                Aggregate aggregate = perDay.computeIfAbsent(day, unused -> new Aggregate());
                if ("earn".equals(entry.getType())) {
                    aggregate.earned += entry.getAmount();
                } else if ("spend".equals(entry.getType())) {
                    aggregate.spent += entry.getAmount();
                }
            });

        List<Map<String, Object>> trends = new ArrayList<>();
        perDay.forEach((day, aggregate) -> {
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", day.toString());
            trend.put("earned", aggregate.earned);
            trend.put("spent", aggregate.spent);
            trends.add(trend);
        });
        return trends;
    }

    private List<Map<String, Object>> buildRecommendations(int familyDbId, Integer childId) {
        Instant lastMonthStart = Instant.now().minus(Duration.ofDays(30));
        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<HistoryEntryEntity> monthlyHistory = queryHistory(familyDbId, childId, lastMonthStart, Instant.now())
            .stream()
            .filter(entry -> "earn".equals(entry.getType()))
            .toList();

        Map<Long, Integer> completionCounts = new LinkedHashMap<>();
        for (HistoryEntryEntity entry : monthlyHistory) {
            if (entry.getRelatedId() == null) {
                continue;
            }
            completionCounts.merge(entry.getRelatedId(), 1, Integer::sum);
        }

        return tasks.stream()
            .sorted(Comparator
                .comparingInt((TaskEntity task) -> completionCounts.getOrDefault(task.getTaskId(), 0))
                .thenComparing(TaskEntity::getCoins, Comparator.reverseOrder()))
            .limit(3)
            .map(task -> {
                int completionCount = completionCounts.getOrDefault(task.getTaskId(), 0);
                Map<String, Object> recommendation = new LinkedHashMap<>();
                recommendation.put("name", task.getName());
                recommendation.put("coins", task.getCoins());
                recommendation.put("reason", completionCount == 0 ? "Давно не выполнялось" : "Стоит повторить");
                return recommendation;
            })
            .toList();
    }

    private static final class Aggregate {
        private int coins;
        private int count;
        private int earned;
        private int spent;
    }

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity h) {
        return new HistoryEntryDto(h.getExternalId(), h.getType(), h.getAmount(),
            h.getDescription(), h.getMoneyAmount(), h.getRelatedId(), h.getGroupName(),
            h.getComment(), h.getCreatedAt() != null ? h.getCreatedAt().toString() : null,
            h.getChildId());
    }

    private RequestDto toRequestDto(PurchaseRequestEntity r) {
        return new RequestDto(r.getId(), r.getTaskId(), r.getTaskName(),
            r.getItemId(), null, r.getCoins(), r.getStatus(), r.getRequestType(),
            r.getMoneyAmount(), r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
            r.getChildId(), null, null, null);
    }
}
