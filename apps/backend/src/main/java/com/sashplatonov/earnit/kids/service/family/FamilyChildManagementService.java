package com.sashplatonov.earnit.kids.service.family;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.ChildStatus;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.sashplatonov.earnit.kids.service.analytics.AnalyticsService;
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
class FamilyChildManagementService {
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    OperationResult<ChildInfo> createChild(String familyId, String childName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            log.warn("createChild failed: family not found familyId={}", familyId);
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        int familyDbId = dbIdOpt.get();

        if (childName == null || childName.isBlank()) {
            return ServiceResults.failure("CHILD_NAME_REQUIRED", "family.childNameRequired");
        }
        if (childName.length() > 50) {
            return ServiceResults.failure("CHILD_NAME_TOO_LONG", "family.childNameTooLong");
        }
        if (childRepository.isNicknameTaken(familyDbId, childName, null)) {
            return ServiceResults.failure("CHILD_NAME_TAKEN", "family.childNameTaken");
        }

        Optional<ChildEntity> childOpt = childRepository.createChild(familyDbId, childName);
        if (childOpt.isEmpty()) {
            log.error("createChild failed: repository returned empty familyId={}", familyId);
            return ServiceResults.failure("CHILD_CREATE_FAILED", "family.createFailed");
        }

        ChildEntity child = childOpt.get();
        log.info("Child created childId={} familyId={}", child.getId(), familyId);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(new ChildInfo(child.getId(), child.getName(), child.getToken()));
    }

    OperationResult<Void> deleteChild(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            log.warn("deleteChild failed: family not found familyId={} childId={}", familyId, childId);
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        var childOpt = childRepository.findByIdOptional(childId);
        if (childOpt.isEmpty() || !Objects.equals(childOpt.get().getFamilyDbId(), dbIdOpt.get())) {
            log.warn("deleteChild failed: child not found or family mismatch familyId={} childId={}", familyId, childId);
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        childRepository.deleteChild(childId);
        log.info("Child deleted childId={} familyId={}", childId, familyId);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    OperationResult<Void> updateNickname(String familyId, int childId, String newName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        if (newName == null || newName.isBlank()) {
            return ServiceResults.failure("NAME_REQUIRED", "family.nameRequired");
        }
        if (childRepository.isNicknameTaken(dbIdOpt.get(), newName, childId)) {
            return ServiceResults.failure("CHILD_NAME_TAKEN", "family.childNameTaken");
        }

        childRepository.updateName(childId, newName);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    OperationResult<Void> updateChildSettings(String familyId, int childId,
                                              String name, int dailyCoinLimit,
                                              int monthlyLimit, Integer dailyRewardLimit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        Optional<ChildEntity> existing = findFamilyChild(dbIdOpt.get(), childId);
        if (existing.isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        // EXPLAIN: A missing daily_reward_limit means "keep the current value",
        // EXPLAIN: so older clients that do not send it cannot reset the limit.
        int resolvedRewardLimit = dailyRewardLimit != null ? dailyRewardLimit : existing.get().getDailyRewardLimit();
        childRepository.updateSettings(childId, name, dailyCoinLimit, monthlyLimit, resolvedRewardLimit);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    OperationResult<Void> updateChildTheme(String familyId, int childId, ChildTheme theme) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (theme == null) {
            return ServiceResults.failure("INVALID_THEME", "family.invalidTheme", Map.of("theme", "null"));
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        childRepository.updateTheme(childId, theme);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    OperationResult<Void> updateChildGroupOrder(String familyId, int childId,
                                                GroupOrderSection section, List<String> groups,
                                                List<String> hiddenGroups, boolean personalOrder) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        if (section == null) {
            return ServiceResults.failure("INVALID_GROUP_ORDER_SECTION", "family.invalidGroupOrderSection",
                Map.of("section", "null"));
        }

        String serializedOrder;
        String serializedHidden = null;
        try {
            serializedOrder = serializeGroupOrder(groups);
            if (!personalOrder) {
                serializedHidden = serializeGroupOrder(hiddenGroups);
            }
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize group order familyId={} childId={} section={}", familyId, childId, section, ex);
            return ServiceResults.failure("GROUP_ORDER_SAVE_FAILED", "family.groupOrderSaveFailed");
        }

        childRepository.updateGroupOrder(childId, section, personalOrder, serializedOrder, serializedHidden);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    OperationResult<String> getChildLoginLink(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        var childOpt = findFamilyChild(dbIdOpt.get(), childId);
        if (childOpt.isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        return OperationResult.success(childOpt.get().getToken());
    }

    OperationResult<String> regenerateChildToken(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        Optional<String> newToken = childRepository.regenerateToken(childId);
        if (newToken.isEmpty()) {
            return ServiceResults.failure("TOKEN_GENERATION_FAILED", "family.tokenGenerationFailed");
        }
        return OperationResult.success(newToken.get());
    }

    OperationResult<Void> setChildActive(String familyId, int childId, boolean active) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        ChildStatus status = active ? ChildStatus.ACTIVE : ChildStatus.INACTIVE;
        childRepository.updateStatus(childId, status.name());
        log.info("Child status updated childId={} familyId={} status={}", childId, familyId, status);
        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    OperationResult<List<ChildDto>> listInactiveChildren(String familyId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        List<ChildDto> children = childRepository.getInactiveChildren(dbIdOpt.get()).stream()
            .map(child -> FamilyDashboardMapper.INSTANCE.toChildDto(child, objectMapper))
            .toList();
        return OperationResult.success(children);
    }

    private void invalidateAnalyticsCache(String familyId) {
        analyticsService.invalidateCache(familyId);
    }

    private Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    private String serializeGroupOrder(List<String> groups) throws JsonProcessingException {
        List<String> normalizedGroups = normalizeGroupOrder(groups);
        if (normalizedGroups.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(normalizedGroups);
    }

    private List<String> normalizeGroupOrder(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String group : groups) {
            if (group == null) {
                continue;
            }

            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        return List.copyOf(normalized);
    }
}
