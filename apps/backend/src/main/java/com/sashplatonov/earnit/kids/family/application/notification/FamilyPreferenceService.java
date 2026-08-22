package com.sashplatonov.earnit.kids.family.application.notification;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.api.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.util.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsService;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyPreferenceService {
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final AnalyticsService analyticsService;

    public OperationResult<Void> updatePreference(String familyId, FamilyPreferenceKey key, Object value) {
        if (key == FamilyPreferenceKey.lastSelectedChildId) {
            Optional<Integer> familyDbIdOpt = familyRepository.getDbId(familyId);
            if (familyDbIdOpt.isEmpty()) {
                return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            Integer childId = parseChildIdPreference(value);
            if (value != null && childId == null) {
                return ServiceResults.failure("INVALID_CHILD_ID", "family.invalidChildId");
            }
            if (childId != null) {
                Optional<ChildEntity> childOpt = childRepository.findByIdOptional(childId);
                if (childOpt.isEmpty() || !Objects.equals(childOpt.get().getFamilyDbId(), familyDbIdOpt.get())) {
                    return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
                }
            }

            familyRepository.updateLastSelectedChild(familyId, childId);
            analyticsService.invalidateCache(familyId);
            return OperationResult.success(null);
        }
        return ServiceResults.failure("UNKNOWN_SETTING", "family.unknownSetting", Map.of("key", String.valueOf(key)));
    }

    private Integer parseChildIdPreference(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
