package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuperAdminService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyService familyService;
    private final BaseDataService baseDataService;
    private final ObjectMapper objectMapper;
    private final PasswordHasher passwordHasher;

    public List<Map<String, Object>> getFamilies() {
        return familyRepository.listAll().stream()
            .sorted(Comparator.comparing(FamilyEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::toFamilySummary)
            .toList();
    }

    public Map<String, Object> getFamilyDetails(String familyId) {
        Optional<FamilyEntity> familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return null;
        }

        FamilyEntity family = familyOpt.get();
        List<ChildEntity> children = childRepository.getChildren(family.getId());

        Map<String, Object> familyInfo = new LinkedHashMap<>();
        familyInfo.put("id", family.getFamilyId());
        familyInfo.put("email", family.getEmail());
        familyInfo.put("created_at", toIso(family.getCreatedAt()));
        familyInfo.put("last_activity", toIso(family.getLastActivity()));
        familyInfo.put("isBlocked", family.isBlocked());
        familyInfo.put("childrenCount", children.size());
        familyInfo.put("children", children.stream().map(this::toChildSummary).toList());
        familyInfo.put("monthly_limit", children.stream().map(ChildEntity::getMonthlyLimit).findFirst().orElse(0));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", children.stream().mapToInt(ChildEntity::getBalance).sum());
        data.put(
            "tasks",
            taskRepository.getTasksForFamily(family.getId()).stream().map(this::toTaskPayload).toList()
        );
        data.put(
            "shop",
            shopItemRepository.getShopItemsForFamily(family.getId()).stream().map(this::toShopPayload).toList()
        );
        data.put(
            "history",
            historyRepository.getHistoryForFamily(family.getId(), 100, 0).stream()
                .map(this::toHistoryPayload)
                .toList()
        );
        data.put(
            "requests",
            purchaseRequestRepository.getRequests(family.getId(), 100, 0).stream()
                .map(this::toRequestPayload)
                .toList()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("familyId", family.getFamilyId());
        payload.put("familyInfo", familyInfo);
        payload.put("data", data);
        return payload;
    }

    public Map<String, Object> getBaseData() {
        return baseDataService.getBaseData();
    }

    public boolean saveBaseData(Map<String, Object> payload) {
        return baseDataService.saveBaseData(payload);
    }

    @Transactional
    public OperationResult<Void> setFamilyPassword(String familyId, String newPassword) {
        if (!isValidPassword(newPassword)) {
            return OperationResult.failure("WEAK_PASSWORD", BackendMessages.message("auth.weakPassword"));
        }

        Optional<FamilyEntity> familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("FAMILY_NOT_FOUND", BackendMessages.message("family.familyNotFound"));
        }

        FamilyEntity family = familyOpt.get();
        if (isSamePassword(newPassword, family.getAdminPassword())) {
            return OperationResult.failure(
                "PASSWORD_REUSE",
                BackendMessages.message("super.newPasswordMustDifferCurrent")
            );
        }

        boolean updated = familyRepository.updatePassword(familyId, passwordHasher.hash(newPassword));
        if (!updated) {
            return OperationResult.failure(
                "PASSWORD_UPDATE_FAILED",
                BackendMessages.message("auth.passwordUpdateFailed")
            );
        }
        return OperationResult.success(null);
    }

    @Transactional
    public boolean setFamilyBlocked(String familyId, boolean blocked) {
        boolean updated = familyRepository.setBlocked(familyId, blocked);
        if (updated) {
            familyRepository.updateLastActivity(familyId);
        }
        return updated;
    }

    public OperationResult<String> regenerateFamilyToken(String familyId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("FAMILY_NOT_FOUND", BackendMessages.message("family.familyNotFound"));
        }
        List<ChildEntity> children = childRepository.getChildren(familyDbId.get());
        if (children.isEmpty()) {
            return OperationResult.failure(
                "FAMILY_HAS_NO_CHILDREN",
                BackendMessages.message("super.familyHasNoChildren")
            );
        }
        return familyService.regenerateChildToken(familyId, children.getFirst().getId());
    }

    public OperationResult<String> regenerateChildToken(int childId) {
        Optional<ChildEntity> child = childRepository.findByIdOptional(childId);
        if (child.isEmpty()) {
            return OperationResult.failure("CHILD_NOT_FOUND", BackendMessages.message("family.childNotFound"));
        }

        Optional<FamilyEntity> family = familyRepository.findByDbId(child.get().getFamilyDbId());
        if (family.isEmpty()) {
            return OperationResult.failure("FAMILY_NOT_FOUND", BackendMessages.message("family.familyNotFound"));
        }

        return familyService.regenerateChildToken(family.get().getFamilyId(), childId);
    }

    private Map<String, Object> toFamilySummary(FamilyEntity family) {
        List<ChildEntity> children = childRepository.getChildren(family.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", family.getFamilyId());
        payload.put("email", family.getEmail());
        payload.put("created_at", toIso(family.getCreatedAt()));
        payload.put("last_activity", toIso(family.getLastActivity()));
        payload.put("isBlocked", family.isBlocked());
        payload.put("tasksCount", taskRepository.getTasksForFamily(family.getId()).size());
        payload.put("shopCount", shopItemRepository.getShopItemsForFamily(family.getId()).size());
        payload.put("childrenCount", children.size());
        payload.put("children", children.stream().map(this::toChildSummary).toList());
        return payload;
    }

    private Map<String, Object> toChildSummary(ChildEntity child) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", child.getId());
        payload.put("name", child.getName());
        payload.put("balance", child.getBalance());
        payload.put("token", child.getToken());
        payload.put("monthly_limit", child.getMonthlyLimit());
        payload.put("daily_coin_limit", child.getDailyCoinLimit());
        return payload;
    }

    private Map<String, Object> toTaskPayload(TaskEntity task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", task.getTaskId());
        payload.put("name", task.getName());
        payload.put("coins", task.getCoins());
        payload.put("group", task.getGroupName());
        payload.put("frequency", parseFrequency(task.getFrequency()));
        payload.put("money_limit", task.getMoneyLimit());
        payload.put("comment", task.getComment());
        payload.put("childId", task.getChildId());
        return payload;
    }

    private Map<String, Object> toShopPayload(ShopItemEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getItemId());
        payload.put("name", item.getName());
        payload.put("price", item.getPrice());
        payload.put("group", item.getGroupName());
        payload.put("frequency", parseFrequency(item.getFrequency()));
        payload.put("money_limit", item.getMoneyLimit());
        payload.put("comment", item.getComment());
        payload.put("childId", item.getChildId());
        return payload;
    }

    private Map<String, Object> toHistoryPayload(HistoryEntryEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getExternalId() != null ? item.getExternalId() : item.getId());
        payload.put("timestamp", toIso(item.getCreatedAt()));
        payload.put(
            "action",
            firstNonBlank(item.getDescription(), item.getType() != null ? item.getType().name() : null)
        );
        payload.put("type", item.getType());
        payload.put("amount", item.getAmount());
        payload.put("childId", item.getChildId());
        return payload;
    }

    private Map<String, Object> toRequestPayload(PurchaseRequestEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getExternalId() != null ? item.getExternalId() : item.getId());
        payload.put("taskId", item.getTaskId());
        payload.put("taskName", item.getTaskName());
        payload.put("itemId", item.getItemId());
        payload.put("coins", item.getCoins());
        payload.put("status", item.getStatus());
        payload.put("requestType", item.getRequestType());
        payload.put("moneyAmount", item.getMoneyAmount());
        payload.put("createdAt", toIso(item.getCreatedAt()));
        payload.put("childId", item.getChildId());
        return payload;
    }

    private Object parseFrequency(JsonNode rawFrequency) {
        if (rawFrequency == null || rawFrequency.isNull()) {
            return null;
        }
        if (rawFrequency.isTextual()) {
            String value = rawFrequency.asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(value, Object.class);
            } catch (Exception ignored) {
                return value;
            }
        }
        return objectMapper.convertValue(rawFrequency, Object.class);
    }

    private String toIso(Instant value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private boolean isSamePassword(String suppliedPassword, String storedPassword) {
        if (storedPassword == null || suppliedPassword == null) {
            return false;
        }
        if (storedPassword.equals(suppliedPassword)) {
            return true;
        }
        try {
            if (passwordHasher.isArgon2Hash(storedPassword)
                && passwordHasher.verify(storedPassword, suppliedPassword)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return passwordHasher.verifyLegacy(suppliedPassword, storedPassword);
    }

    private boolean isValidPassword(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        char first = password.charAt(0);
        return !password.chars().allMatch(c -> c == first);
    }
}
