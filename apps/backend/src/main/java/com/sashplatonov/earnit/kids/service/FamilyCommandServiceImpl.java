package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.repository.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyCommandServiceImpl implements FamilyCommandService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FamilyDashboardQueryService familyDashboardQueryService;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                              Map<String, Object> payload,
                                                              boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = dbIdOpt.get();
        List<ChildEntity> children = childRepository.getChildren(familyDbId);
        if (children.isEmpty()) {
            return familyDashboardQueryService.loadFamilyData(familyId, childId, adminSession);
        }

        List<ChildEntity> accessibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (accessibleChildren.isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        Integer selectedChildId = resolveSelectedChildId(familyId, childId, payload, accessibleChildren, adminSession);
        if (selectedChildId != null
            && accessibleChildren.stream().noneMatch(child -> Objects.equals(child.getId(), selectedChildId))) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        syncFamilyRules(familyId, payload, adminSession);
        syncBalances(familyDbId, selectedChildId, payload, accessibleChildren);
        syncTasks(familyDbId, selectedChildId, payload);
        syncShopItems(familyDbId, selectedChildId, payload);
        familyRepository.updateLastActivity(familyId);
        analyticsService.invalidateCache(familyId);

        return familyDashboardQueryService.loadFamilyData(familyId, selectedChildId, adminSession);
    }

    private void syncFamilyRules(String familyId, Map<String, Object> payload, boolean adminSession) {
        if (!adminSession || !payload.containsKey("rules")) {
            return;
        }

        familyRepository.updateRules(familyId, asNullableString(payload.get("rules")));
    }

    private Integer resolveSelectedChildId(String familyId, Integer explicitChildId,
                                           Map<String, Object> payload,
                                           List<ChildEntity> children,
                                           boolean adminSession) {
        if (!adminSession) {
            return explicitChildId != null ? explicitChildId : children.getFirst().getId();
        }

        if (explicitChildId != null) {
            return explicitChildId;
        }

        Integer inferredChildId = inferSingleChildId(payload);
        if (inferredChildId != null) {
            return inferredChildId;
        }

        Integer persistedChildId = familyRepository.getLastSelectedChildId(familyId).orElse(null);
        if (persistedChildId != null
            && children.stream().anyMatch(child -> Objects.equals(child.getId(), persistedChildId))) {
            return persistedChildId;
        }

        return children.getFirst().getId();
    }

    private List<ChildEntity> resolveVisibleChildren(List<ChildEntity> children,
                                                     boolean adminSession,
                                                     Integer childId) {
        if (adminSession) {
            return children;
        }
        if (childId == null) {
            return List.of();
        }
        return children.stream()
            .filter(child -> Objects.equals(child.getId(), childId))
            .toList();
    }

    private void syncBalances(int familyDbId, Integer selectedChildId,
                              Map<String, Object> payload,
                              List<ChildEntity> children) {
        if (selectedChildId != null) {
            Integer currentBalance = asInteger(payload.get("balance"));
            if (currentBalance != null) {
                childRepository.updateBalance(selectedChildId, currentBalance);
            }
        }

        Set<Integer> allowedChildIds = children.stream()
            .map(ChildEntity::getId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (Map<String, Object> childPayload : asMapList(payload.get("children"))) {
            Integer childId = asInteger(childPayload.get("id"));
            Integer balance = asInteger(childPayload.get("balance"));
            if (childId == null
                || balance == null
                || !allowedChildIds.contains(childId)
                || Objects.equals(childId, selectedChildId)) {
                continue;
            }
            childRepository.updateBalance(childId, balance);
        }
    }

    private void syncTasks(int familyDbId, Integer selectedChildId, Map<String, Object> payload) {
        if (selectedChildId == null || !payload.containsKey("tasks")) {
            return;
        }

        taskRepository.markAllTasksDeleted(selectedChildId);
        for (Map<String, Object> task : asMapList(payload.get("tasks"))) {
            Long taskId = asLong(task.get("id"));
            String name = asString(task.get("name"));
            if (taskId == null || name == null || name.isBlank()) {
                continue;
            }

            taskRepository.upsertTask(new TaskUpsertCommand(
                familyDbId,
                selectedChildId,
                taskId,
                name,
                defaultInt(task.get("coins"), 0),
                firstNonBlank(asString(task.get("groupName")), asString(task.get("group"))),
                serializeFrequency(task.get("frequency")),
                asString(task.get("comment")),
                coalesceInt(task.get("moneyLimit"), task.get("money_limit")),
                defaultBoolean(coalesceFirst(task.get("isActive"), task.get("is_active")), true),
                defaultBoolean(task.get("isDeleted"), false)
            ));
        }
    }

    private void syncShopItems(int familyDbId, Integer selectedChildId, Map<String, Object> payload) {
        if (selectedChildId == null || !payload.containsKey("shop")) {
            return;
        }

        shopItemRepository.markAllShopItemsDeleted(selectedChildId);
        for (Map<String, Object> item : asMapList(payload.get("shop"))) {
            Long itemId = asLong(item.get("id"));
            String name = asString(item.get("name"));
            if (itemId == null || name == null || name.isBlank()) {
                continue;
            }

            shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(
                familyDbId,
                selectedChildId,
                itemId,
                name,
                defaultInt(item.get("price"), 0),
                firstNonBlank(asString(item.get("groupName")), asString(item.get("group"))),
                serializeFrequency(item.get("frequency")),
                asString(item.get("comment")),
                coalesceInt(item.get("moneyLimit"), item.get("money_limit")),
                defaultBoolean(coalesceFirst(item.get("isActive"), item.get("is_active")), true),
                defaultBoolean(item.get("isDeleted"), false)
            ));
        }
    }

    private Integer inferSingleChildId(Map<String, Object> payload) {
        Set<Integer> childIds = new LinkedHashSet<>();
        collectChildIds(childIds, payload.get("tasks"));
        collectChildIds(childIds, payload.get("shop"));
        collectChildIds(childIds, payload.get("history"));
        return childIds.size() == 1 ? childIds.iterator().next() : null;
    }

    private void collectChildIds(Set<Integer> childIds, Object rawEntries) {
        for (Map<String, Object> entry : asMapList(rawEntries)) {
            Integer entryChildId = asInteger(entry.get("childId"));
            if (entryChildId != null) {
                childIds.add(entryChildId);
            }
        }
    }

    private List<Map<String, Object>> asMapList(Object rawValue) {
        if (!(rawValue instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(objectMapper.convertValue(
                    map,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }
                ));
            }
        }
        return result;
    }

    private String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isBlank() ? null : s;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private JsonNode serializeFrequency(Object rawFrequency) {
        if (rawFrequency == null) {
            return null;
        }
        if (rawFrequency instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (rawFrequency instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(text);
            } catch (Exception ex) {
                log.warn("Failed to parse frequency payload as JSON string: {}", text, ex);
                return TextNode.valueOf(text);
            }
        }

        try {
            return objectMapper.valueToTree(rawFrequency);
        } catch (Exception ex) {
            log.warn("Failed to convert frequency payload to JSON: {}", rawFrequency, ex);
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer coalesceInt(Object primary, Object fallback) {
        Integer primaryValue = asInteger(primary);
        return primaryValue != null ? primaryValue : asInteger(fallback);
    }

    private Object coalesceFirst(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private int defaultInt(Object value, int defaultValue) {
        Integer parsed = asInteger(value);
        return parsed != null ? parsed : defaultValue;
    }

    private boolean defaultBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }
}
