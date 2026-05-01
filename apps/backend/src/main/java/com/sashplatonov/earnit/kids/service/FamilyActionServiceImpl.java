package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyActionServiceImpl implements FamilyActionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter PERIOD_RESET_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final int MAX_REQUEST_NOTE_LENGTH = 120;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyService familyService;
    private final TimeProvider timeProvider;

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> completeTask(String familyId, int childId, long taskId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<TaskEntity> task = findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        child.get().setBalance(child.get().getBalance() + task.get().getCoins());
        historyRepository.persist(buildTaskHistory(familyDbId.get(), childId, task.get()));
        return familyService.loadFamilyData(familyId, childId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> requestTaskCompletion(String familyId, int childId, long taskId, String note) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<TaskEntity> task = findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        String taskLimitError = validateTaskRequestLimit(
            familyDbId.get(),
            childId,
            task.get()
        );
        if (taskLimitError != null) {
            return OperationResult.failure("TASK_REQUEST_LIMIT_REACHED", taskLimitError);
        }

        OperationResult<String> normalizedNoteResult = validateAndNormalizeRequestNote(note);
        if (normalizedNoteResult instanceof OperationResult.Failure<String> failure) {
            return OperationResult.failure(failure.errorCode(), failure.message());
        }

        String normalizedNote = normalizedNoteResult instanceof OperationResult.Success<String> success
            ? success.value()
            : null;

        purchaseRequestRepository.persist(buildTaskRequest(familyDbId.get(), childId, task.get(), normalizedNote));
        return familyService.loadFamilyData(familyId, childId, false);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<ShopItemEntity> item = findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        if (child.get().getBalance() < item.get().getPrice()) {
            return OperationResult.failure(BackendMessages.message("balance.insufficient"));
        }

        child.get().setBalance(child.get().getBalance() - item.get().getPrice());
        historyRepository.persist(buildShopHistory(familyDbId.get(), childId, item.get()));
        return familyService.loadFamilyData(familyId, childId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> requestItemPurchase(String familyId, int childId, long itemId, String note) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<ShopItemEntity> item = findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        String itemLimitError = validateItemRequestLimit(
            familyDbId.get(),
            childId,
            item.get()
        );
        if (itemLimitError != null) {
            return OperationResult.failure("ITEM_REQUEST_LIMIT_REACHED", itemLimitError);
        }

        OperationResult<String> normalizedNoteResult = validateAndNormalizeRequestNote(note);
        if (normalizedNoteResult instanceof OperationResult.Failure<String> failure) {
            return OperationResult.failure(failure.errorCode(), failure.message());
        }

        String normalizedNote = normalizedNoteResult instanceof OperationResult.Success<String> success
            ? success.value()
            : null;

        purchaseRequestRepository.persist(buildPurchaseRequest(familyDbId.get(), childId, item.get(), normalizedNote));
        return familyService.loadFamilyData(familyId, childId, false);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> approveRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<PurchaseRequestEntity> request = findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("requests.notFound"));
        }
        if (!isPending(request.get())) {
            return OperationResult.failure(BackendMessages.message("requests.alreadyProcessed"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), request.get().getChildId());
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        if (isPurchaseRequest(request.get())) {
            if (child.get().getBalance() < request.get().getCoins()) {
                return OperationResult.failure(BackendMessages.message("balance.insufficient"));
            }
            child.get().setBalance(child.get().getBalance() - request.get().getCoins());
        } else {
            child.get().setBalance(child.get().getBalance() + request.get().getCoins());
        }

        historyRepository.persist(buildRequestHistory(familyDbId.get(), request.get()));
        request.get().setStatus("approved");
        int responseChildId = resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        return familyService.loadFamilyData(familyId, responseChildId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> rejectRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<PurchaseRequestEntity> request = findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("requests.notFound"));
        }
        if (!isPending(request.get())) {
            return OperationResult.failure(BackendMessages.message("requests.alreadyProcessed"));
        }

        request.get().setStatus("rejected");
        int responseChildId = resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        return familyService.loadFamilyData(familyId, responseChildId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> deleteRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<PurchaseRequestEntity> request = findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("requests.notFound"));
        }

        // EXPLAIN: Child sessions can only delete their own requests, and only while not approved yet.
        // EXPLAIN: `currentChildId` historically served as a *response childId hint* for admin sessions,
        // EXPLAIN: so we only apply child-only rules when it is clear the caller is the request owner.
        boolean isChildDeletingOwnRequest = currentChildId != null
            && Objects.equals(request.get().getChildId(), currentChildId);
        if (isChildDeletingOwnRequest) {
            // EXPLAIN: Not yet approved => allow deleting pending or rejected.
            if ("approved".equals(request.get().getStatus())) {
                return OperationResult.failure(BackendMessages.message("requests.alreadyProcessed"));
            }
        }

        int responseChildId = resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        purchaseRequestRepository.delete(request.get());
        return familyService.loadFamilyData(familyId, responseChildId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<HistoryEntryEntity> historyEntry = findHistoryEntry(familyDbId.get(), childId, historyEntryId);
        if (historyEntry.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("history.entryNotFound"));
        }

        int delta = "earn".equals(historyEntry.get().getType())
            ? -historyEntry.get().getAmount()
            : historyEntry.get().getAmount();
        child.get().setBalance(child.get().getBalance() + delta);
        historyRepository.delete(historyEntry.get());
        return familyService.loadFamilyData(familyId, childId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> adjustBalance(String familyId, int childId, int amount, String description) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }
        if (amount == 0) {
            return OperationResult.failure(BackendMessages.message("balance.amountZero"));
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        child.get().setBalance(child.get().getBalance() + amount);
        historyRepository.persist(buildAdjustmentHistory(familyDbId.get(), childId, amount, description));
        return familyService.loadFamilyData(familyId, childId, true);
    }

    private Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    private Optional<TaskEntity> findActiveTask(int familyDbId, int childId, long taskId) {
        return taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false AND active = true",
            familyDbId,
            childId,
            taskId
        ).firstResultOptional();
    }

    private Optional<ShopItemEntity> findActiveItem(int familyDbId, int childId, long itemId) {
        return shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            familyDbId,
            childId,
            itemId
        ).firstResultOptional();
    }

    private Optional<PurchaseRequestEntity> findFamilyRequest(int familyDbId, long requestId) {
        return purchaseRequestRepository.findByIdOptional(requestId)
            .filter(request -> request.getFamilyId() == familyDbId);
    }

    private Optional<HistoryEntryEntity> findHistoryEntry(int familyDbId, int childId, long historyEntryId) {
        return historyRepository.find(
            "familyId = ?1 AND childId = ?2 AND externalId = ?3",
            familyDbId,
            childId,
            historyEntryId
        ).firstResultOptional();
    }

    private String validateTaskRequestLimit(int familyDbId, int childId, TaskEntity task) {
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

        return usedCount >= limit ? buildTaskLimitReachedMessage(period, windowEnd) : null;
    }

    private String validateItemRequestLimit(int familyDbId, int childId, ShopItemEntity item) {
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

        return usedCount >= limit ? buildItemLimitReachedMessage(period, windowEnd) : null;
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
            case "week", "month", "year" -> period;
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
            default -> currentPeriodStart.atZone(ZoneId.systemDefault())
                .plusDays(1)
                .toInstant();
        };
    }

    private String buildTaskLimitReachedMessage(String period, Instant resetAt) {
        return BackendMessages.taskLimitReached(period, formatResetAt(resetAt, period));
    }

    private String buildItemLimitReachedMessage(String period, Instant resetAt) {
        return BackendMessages.itemLimitReached(period, formatResetAt(resetAt, period));
    }

    private String formatResetAt(Instant resetAt, String period) {
        var zonedResetAt = resetAt.atZone(ZoneId.systemDefault());
        if ("day".equals(period)) {
            return zonedResetAt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return zonedResetAt.format(PERIOD_RESET_FORMATTER);
    }

    private int resolveResponseChildId(int familyDbId, Integer currentChildId, int fallbackChildId) {
        if (currentChildId != null && findFamilyChild(familyDbId, currentChildId).isPresent()) {
            return currentChildId;
        }
        return fallbackChildId;
    }

    private boolean isPending(PurchaseRequestEntity request) {
        return request.getStatus() == null || "pending".equals(request.getStatus());
    }

    private boolean isPurchaseRequest(PurchaseRequestEntity request) {
        return "shop_purchase".equals(request.getRequestType());
    }

    private PurchaseRequestEntity buildTaskRequest(int familyDbId, int childId, TaskEntity task, String note) {
        return PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .taskId(task.getTaskId())
            .taskName(task.getName())
            .coins(task.getCoins())
            .status("pending")
            .requestType("earn")
            .moneyAmount(0)
            .note(note)
            .createdAt(now())
            .build();
    }

    private PurchaseRequestEntity buildPurchaseRequest(int familyDbId, int childId, ShopItemEntity item, String note) {
        return PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .taskId(item.getItemId())
            .taskName(item.getName())
            .itemId(item.getItemId())
            .coins(item.getPrice())
            .status("pending")
            .requestType("shop_purchase")
            .moneyAmount(item.getMoneyLimit() != null ? item.getMoneyLimit() : 0)
            .note(note)
            .createdAt(now())
            .build();
    }

    // EXPLAIN: Note is optional; empty -> null.
    // EXPLAIN: Constraints: single-line only, max 120 chars.
    private OperationResult<String> validateAndNormalizeRequestNote(String note) {
        if (note == null) {
            return OperationResult.success(null);
        }

        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return OperationResult.success(null);
        }

        // EXPLAIN: one line only
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return OperationResult.failure("REQUEST_NOTE_INVALID", BackendMessages.message("requests.noteInvalid"));
        }

        if (trimmed.length() > MAX_REQUEST_NOTE_LENGTH) {
            return OperationResult.failure("REQUEST_NOTE_TOO_LONG", BackendMessages.message("requests.noteTooLong"));
        }

        return OperationResult.success(trimmed);
    }

    private HistoryEntryEntity buildTaskHistory(int familyDbId, int childId, TaskEntity task) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type("earn")
            .amount(task.getCoins())
            .description(task.getName())
            .moneyAmount(0)
            .relatedId(task.getTaskId())
            .groupName(task.getGroupName())
            .comment(task.getComment())
            .createdAt(now())
            .build();
    }

    private HistoryEntryEntity buildShopHistory(int familyDbId, int childId, ShopItemEntity item) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type("spend")
            .amount(item.getPrice())
            .description(item.getName())
            .moneyAmount(item.getMoneyLimit() != null ? item.getMoneyLimit() : 0)
            .relatedId(item.getItemId())
            .groupName(item.getGroupName())
            .comment(item.getComment())
            .createdAt(now())
            .build();
    }

    private HistoryEntryEntity buildRequestHistory(int familyDbId, PurchaseRequestEntity request) {
        Instant requestCreatedAt = request.getCreatedAt() != null ? request.getCreatedAt() : now();
        if (isPurchaseRequest(request)) {
            Optional<ShopItemEntity> item = request.getItemId() == null
                ? Optional.empty()
                : findActiveItem(familyDbId, request.getChildId(), request.getItemId());
            return HistoryEntryEntity.builder()
                .familyId(familyDbId)
                .childId(request.getChildId())
                .externalId(nextExternalId())
                .type("spend")
                .amount(request.getCoins())
                .description(item.map(ShopItemEntity::getName).orElse(request.getTaskName()))
                .moneyAmount(request.getMoneyAmount())
                .relatedId(request.getItemId() != null ? request.getItemId() : request.getTaskId())
                .groupName(item.map(ShopItemEntity::getGroupName).orElse(null))
                .comment(item.map(ShopItemEntity::getComment).orElse(null))
                .createdAt(requestCreatedAt)
                .build();
        }

        Optional<TaskEntity> task = request.getTaskId() == null
            ? Optional.empty()
            : findActiveTask(familyDbId, request.getChildId(), request.getTaskId());
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(request.getChildId())
            .externalId(nextExternalId())
            .type("earn")
            .amount(request.getCoins())
            .description(task.map(TaskEntity::getName).orElse(request.getTaskName()))
            .moneyAmount(0)
            .relatedId(request.getTaskId())
            .groupName(task.map(TaskEntity::getGroupName).orElse(null))
            .comment(task.map(TaskEntity::getComment).orElse(null))
            .createdAt(requestCreatedAt)
            .build();
    }

    private HistoryEntryEntity buildAdjustmentHistory(int familyDbId, int childId, int amount, String description) {
        String normalizedDescription = description != null && !description.isBlank()
            ? description.trim()
            : amount > 0 ? BackendMessages.message("balance.adjustmentCredit") : BackendMessages.message("balance.adjustmentDebit");
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type(amount > 0 ? "earn" : "spend")
            .amount(Math.abs(amount))
            .description(normalizedDescription)
            .moneyAmount(0)
            .createdAt(now())
            .build();
    }

    private Instant now() {
        return timeProvider.now();
    }

    private long nextExternalId() {
        long value = timeProvider.now().toEpochMilli();
        return value > 0 ? value : 1L;
    }
}