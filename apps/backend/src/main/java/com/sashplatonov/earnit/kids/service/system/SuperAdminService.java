package com.sashplatonov.earnit.kids.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.config.auth.PasswordHasher;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.SuperAdminFamiliesResponse;
import com.sashplatonov.earnit.kids.dto.response.SuperAdminFamilyDetailsResponse;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.family.FamilyOperationGuard;
import com.sashplatonov.earnit.kids.service.database.BaseDataService;
import com.sashplatonov.earnit.kids.service.observability.BackendKpiMetrics;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuperAdminService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyService familyService;
    private final BaseDataService baseDataService;
    private final BackendKpiMetrics backendKpiMetrics;
    private final ObjectMapper objectMapper;
    private final PasswordHasher passwordHasher;
    private final FamilyOperationGuard familyOperationGuard;

    public SuperAdminFamiliesResponse getFamilies() {
        return backendKpiMetrics.recordValue("admin", "families", () ->
            familyRepository.listAll().stream()
                .sorted(Comparator.comparing(FamilyEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toFamilySummary)
                .collect(java.util.stream.Collectors.collectingAndThen(
                    java.util.stream.Collectors.toList(),
                    SuperAdminFamiliesResponse::new
                ))
        );
    }

    public SuperAdminFamilyDetailsResponse getFamilyDetails(String familyId) {
        return backendKpiMetrics.recordValue("admin", "family_details", () -> {
            Optional<FamilyEntity> familyOpt = familyRepository.findById(familyId);
            if (familyOpt.isEmpty()) {
                return null;
            }

            FamilyEntity family = familyOpt.get();
            List<ChildEntity> children = childRepository.getChildren(family.getId());

            return new SuperAdminFamilyDetailsResponse(
                family.getFamilyId(),
                new SuperAdminFamilyDetailsResponse.FamilyInfo(
                    family.getFamilyId(),
                    family.getEmail(),
                    toIso(family.getCreatedAt()),
                    toIso(family.getLastActivity()),
                    family.isBlocked(),
                    children.size(),
                    children.stream().map(this::toChildSummary).toList(),
                    children.stream().map(ChildEntity::getMonthlyLimit).findFirst().orElse(0)
                ),
                new SuperAdminFamilyDetailsResponse.FamilyData(
                    children.stream().mapToInt(ChildEntity::getBalance).sum(),
                    taskRepository.getTasksForFamily(family.getId()).stream().map(this::toTaskPayload).toList(),
                    historyRepository.getHistoryForFamily(family.getId(), 100, 0).stream()
                        .map(this::toHistoryPayload)
                        .toList(),
                    purchaseRequestRepository.getRequests(family.getId(), 100, 0).stream()
                        .map(this::toRequestPayload)
                        .toList()
                )
            );
        });
    }

    public Map<String, Object> getBaseData() {
        return backendKpiMetrics.recordValue("admin", "base_data", baseDataService::getBaseData);
    }

    public boolean saveBaseData(Map<String, Object> payload) {
        return backendKpiMetrics.recordValue("admin", "save_base_data", () -> baseDataService.saveBaseData(payload));
    }

    @Transactional
    public OperationResult<Void> setFamilyPassword(String familyId, String newPassword) {
        return backendKpiMetrics.recordResult("admin", "set_family_password", () -> {
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
        });
    }

    @Transactional
    public boolean setFamilyBlocked(String familyId, boolean blocked) {
        return backendKpiMetrics.recordValue("admin", "set_family_blocked", () -> {
            boolean updated = familyRepository.setBlocked(familyId, blocked);
            if (updated) {
                familyRepository.updateLastActivity(familyId);
            }
            return updated;
        });
    }

    public OperationResult<String> regenerateFamilyToken(String familyId) {
        return backendKpiMetrics.recordResult("admin", "regenerate_family_token", () -> {
            OperationResult<Integer> familyDbIdResult = familyOperationGuard.requireFamilyDbId(familyId);
            if (familyDbIdResult.isFailure()) {
                return familyDbIdResult.asFailure();
            }
            int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();
            List<ChildEntity> children = childRepository.getChildren(familyDbId);
            if (children.isEmpty()) {
                return OperationResult.failure(
                    "FAMILY_HAS_NO_CHILDREN",
                    BackendMessages.message("super.familyHasNoChildren")
                );
            }
            return familyService.regenerateChildToken(familyId, children.getFirst().getId());
        });
    }

    public OperationResult<String> regenerateChildToken(int childId) {
        return backendKpiMetrics.recordResult("admin", "regenerate_child_token", () -> {
            Optional<ChildEntity> child = childRepository.findByIdOptional(childId);
            if (child.isEmpty()) {
                return OperationResult.failure("CHILD_NOT_FOUND", BackendMessages.message("family.childNotFound"));
            }

            Optional<FamilyEntity> family = familyRepository.findByDbId(child.get().getFamilyDbId());
            if (family.isEmpty()) {
                return OperationResult.failure("FAMILY_NOT_FOUND", BackendMessages.message("family.familyNotFound"));
            }

            return familyService.regenerateChildToken(family.get().getFamilyId(), childId);
        });
    }

    private SuperAdminFamiliesResponse.FamilySummary toFamilySummary(FamilyEntity family) {
        List<ChildEntity> children = childRepository.getChildren(family.getId());
        return new SuperAdminFamiliesResponse.FamilySummary(
            family.getFamilyId(),
            family.getEmail(),
            toIso(family.getCreatedAt()),
            toIso(family.getLastActivity()),
            family.isBlocked(),
            taskRepository.getTasksForFamily(family.getId()).size(),
            children.size(),
            children.stream().map(this::toChildSummary).toList()
        );
    }

    private SuperAdminFamiliesResponse.ChildSummary toChildSummary(ChildEntity child) {
        return new SuperAdminFamiliesResponse.ChildSummary(
            child.getId(),
            child.getName(),
            child.getBalance(),
            child.getToken(),
            child.getMonthlyLimit(),
            child.getDailyCoinLimit()
        );
    }

    private TaskDto toTaskPayload(TaskEntity task) {
        return new TaskDto(
            task.getTaskId(),
            task.getName(),
            task.getCoins(),
            task.getGroupName(),
            parseFrequency(task.getFrequency()),
            task.getComment(),
            task.getCueWhen(),
            task.getCueAction(),
            task.getMoneyLimit(),
            !task.isDeleted(),
            task.getChildId(),
            null,
            null
        );
    }

    private HistoryEntryDto toHistoryPayload(HistoryEntryEntity item) {
        String action = firstNonBlank(item.getDescription(), item.getType() != null ? item.getType().name() : null);
        return new HistoryEntryDto(
            item.getExternalId() != null ? item.getExternalId() : item.getId(),
            item.getType(),
            item.getAmount(),
            action,
            item.getDescription(),
            item.getMoneyAmount(),
            item.getRelatedId(),
            null,
            null,
            null,
            null,
            item.getGroupName(),
            item.getComment(),
            toIso(item.getCreatedAt()),
            item.getChildId()
        );
    }

    private RequestDto toRequestPayload(PurchaseRequestEntity item) {
        return new RequestDto(
            item.getExternalId() != null ? item.getExternalId() : item.getId(),
            item.getTaskId(),
            item.getTaskName(),
            item.getItemId(),
            null,
            item.getTaskName(),
            null,
            null,
            item.getNote(),
            null,
            item.getCoins(),
            item.getStatus(),
            item.getRequestType(),
            item.getMoneyAmount(),
            toIso(item.getCreatedAt()),
            item.getChildId(),
            null,
            null,
            null,
            null
        );
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
