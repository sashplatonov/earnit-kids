package com.sashplatonov.earnit.kids.service.family.command;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
final class FamilyCommandSelectionService {
    private final FamilyRepository familyRepository;
    private final FamilyCommandPayloadService payloadService;

    @Inject
    FamilyCommandSelectionService(FamilyRepository familyRepository,
                                  FamilyCommandPayloadService payloadService) {
        this.familyRepository = familyRepository;
        this.payloadService = payloadService;
    }

    List<ChildEntity> resolveVisibleChildren(List<ChildEntity> children,
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

    Integer resolveSelectedChildId(String familyId, Integer explicitChildId,
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

    private Integer inferSingleChildId(Map<String, Object> payload) {
        Set<Integer> childIds = new LinkedHashSet<>();
        collectChildIds(childIds, payload.get("tasks"));
        collectChildIds(childIds, payload.get("shop"));
        collectChildIds(childIds, payload.get("history"));
        return childIds.size() == 1 ? childIds.iterator().next() : null;
    }

    private void collectChildIds(Set<Integer> childIds, Object rawEntries) {
        for (Map<String, Object> entry : payloadService.asMapList(rawEntries)) {
            Integer entryChildId = payloadService.asInteger(entry.get("childId"));
            if (entryChildId != null) {
                childIds.add(entryChildId);
            }
        }
    }
}
