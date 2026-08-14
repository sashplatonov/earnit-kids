package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyNotificationPreferenceEntity;
import com.sashplatonov.earnit.kids.domain.model.ChildStatus;
import com.sashplatonov.earnit.kids.dto.response.ChildNotificationSettingsDto;
import com.sashplatonov.earnit.kids.dto.response.FamilyNotificationSettingsResponse;
import com.sashplatonov.earnit.kids.dto.response.NotificationPreferenceDto;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyNotificationPreferenceRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// EXPLAIN: Role-aware notification preferences. Parent and child keys are
// EXPLAIN: distinct so screens never render meaningless universal lists.
@ApplicationScoped
public class FamilyNotificationServiceImpl implements FamilyNotificationService {
    private static final String SCOPE_PARENT = "parent";
    private static final String SCOPE_CHILD = "child";

    private static final Map<String, Boolean> PARENT_DEFAULTS = new LinkedHashMap<>(Map.of(
        "taskMarkedDone", true,
        "rewardRequested", true,
        "balanceChanged", false,
        "parentInviteAccepted", true,
        "childTelegramLinked", true
    ));

    private static final Map<String, Boolean> CHILD_DEFAULTS = new LinkedHashMap<>(Map.of(
        "taskApproved", true,
        "taskRejected", true,
        "rewardApproved", true,
        "rewardRejected", true,
        "newTasks", true,
        "rewardAvailable", false
    ));

    @Inject private FamilyRepository families;
    @Inject private ChildRepository children;
    @Inject private FamilyNotificationPreferenceRepository preferences;

    FamilyNotificationServiceImpl() {
    }

    @Override
    public OperationResult<FamilyNotificationSettingsResponse> getSettings(String familyId) {
        Optional<Integer> dbIdOpt = families.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        int familyDbId = dbIdOpt.get();

        Map<String, Boolean> stored = new LinkedHashMap<>();
        for (FamilyNotificationPreferenceEntity entity : preferences.findByFamily(familyDbId)) {
            stored.put(entity.getScope() + ":" + entity.getChildId() + ":" + entity.getPrefKey(),
                entity.isEnabled());
        }

        List<NotificationPreferenceDto> parent = PARENT_DEFAULTS.entrySet().stream()
            .map(entry -> preference(entry.getKey(), entry.getValue(), stored.get(SCOPE_PARENT + ":null:" + entry.getKey())))
            .toList();

        List<ChildNotificationSettingsDto> childrenSettings = children.getActiveChildren(familyDbId).stream()
            .map(child -> new ChildNotificationSettingsDto(
                child.getId(),
                child.getName(),
                CHILD_DEFAULTS.entrySet().stream()
                    .map(entry -> preference(entry.getKey(), entry.getValue(),
                        stored.get(SCOPE_CHILD + ":" + child.getId() + ":" + entry.getKey())))
                    .toList()))
            .toList();

        return OperationResult.success(new FamilyNotificationSettingsResponse(parent, childrenSettings));
    }

    @Override
    public OperationResult<Void> setPreference(String familyId, String scope, Integer childId,
                                               String key, boolean enabled) {
        Optional<Integer> dbIdOpt = families.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        int familyDbId = dbIdOpt.get();

        if (SCOPE_PARENT.equals(scope)) {
            if (!PARENT_DEFAULTS.containsKey(key)) {
                return failure("UNKNOWN_PREFERENCE", "family.unknownSetting", Map.of("key", key == null ? "null" : key));
            }
            childId = null;
        } else if (SCOPE_CHILD.equals(scope)) {
            if (!CHILD_DEFAULTS.containsKey(key)) {
                return failure("UNKNOWN_PREFERENCE", "family.unknownSetting", Map.of("key", key == null ? "null" : key));
            }
            if (childId == null || children.findByIdOptional(childId)
                .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId))
                .filter(child -> ChildStatus.ACTIVE.name().equals(child.getStatus()))
                .isEmpty()) {
                return failure("CHILD_NOT_FOUND", "family.childNotFound");
            }
        } else {
            return failure("INVALID_SCOPE", "family.unknownSetting", Map.of("key", "scope"));
        }

        preferences.setEnabled(familyDbId, scope, childId, key, enabled);
        return OperationResult.success(null);
    }

    private static NotificationPreferenceDto preference(String key, boolean fallback, Boolean stored) {
        return new NotificationPreferenceDto(key, stored != null ? stored : fallback);
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey, Map<String, String> variables) {
        return OperationResult.failure(errorCode,
            com.sashplatonov.earnit.kids.i18n.BackendMessages.message(messageKey, variables));
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode,
            com.sashplatonov.earnit.kids.i18n.BackendMessages.message(messageKey));
    }
}
