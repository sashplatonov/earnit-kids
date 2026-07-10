package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardScopeLoader {
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;

    Optional<FamilyDashboardScopeData> loadFamilyScope(String familyId, Integer childId, boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return Optional.empty();
        }

        int familyDbId = dbIdOpt.get();
        String rules = familyRepository.getRules(familyId).orElse(null);
        Integer persistedChildId = familyRepository.getLastSelectedChildId(familyId).orElse(null);
        List<ChildEntity> children = childRepository.getChildren(familyDbId);

        if (children.isEmpty()) {
            return Optional.of(FamilyDashboardScopeData.empty(familyDbId, rules));
        }

        List<ChildEntity> visibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (visibleChildren.isEmpty()) {
            return Optional.empty();
        }

        ChildEntity activeChild = resolveActiveChild(visibleChildren, childId, persistedChildId, adminSession);
        Integer resolvedLastSelectedChildId = resolveLastSelectedChildId(
            children,
            activeChild,
            persistedChildId,
            adminSession
        );
        return Optional.of(new FamilyDashboardScopeData(
            familyDbId,
            rules,
            activeChild,
            visibleChildren,
            resolvedLastSelectedChildId
        ));
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

    private ChildEntity resolveActiveChild(List<ChildEntity> visibleChildren,
                                           Integer requestedChildId,
                                           Integer persistedChildId,
                                           boolean adminSession) {
        Integer preferredChildId = adminSession
            ? (requestedChildId != null ? requestedChildId : persistedChildId)
            : visibleChildren.getFirst().getId();
        if (preferredChildId == null) {
            return visibleChildren.getFirst();
        }
        return visibleChildren.stream()
            .filter(child -> Objects.equals(child.getId(), preferredChildId))
            .findFirst()
            .orElse(visibleChildren.getFirst());
    }

    private Integer resolveLastSelectedChildId(List<ChildEntity> children,
                                               ChildEntity activeChild,
                                               Integer persistedChildId,
                                               boolean adminSession) {
        if (!adminSession) {
            return activeChild.getId();
        }
        return children.stream()
            .map(ChildEntity::getId)
            .filter(id -> Objects.equals(id, persistedChildId))
            .findFirst()
            .orElse(activeChild.getId());
    }
}
