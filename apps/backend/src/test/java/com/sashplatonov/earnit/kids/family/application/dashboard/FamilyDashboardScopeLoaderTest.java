package com.sashplatonov.earnit.kids.family.application.dashboard;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildStatus;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyDashboardScopeLoaderTest {

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;

    private FamilyDashboardScopeLoader loader() {
        return new FamilyDashboardScopeLoader(familyRepository, childRepository);
    }

    private ChildEntity child(int id, String name, String status) {
        return ChildEntity.builder()
            .id(id)
            .familyDbId(1)
            .name(name)
            .token("token-" + id)
            .status(status)
            .build();
    }

    @Test
    void adminSession_hidesInactiveChildrenFromVisibleList() {
        when(familyRepository.getDbId("family-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("family-1")).thenReturn(Optional.empty());
        when(familyRepository.getLastSelectedChildId("family-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(
            child(10, "Маша", ChildStatus.ACTIVE.name()),
            child(11, "Петя", ChildStatus.INACTIVE.name())
        ));

        Optional<FamilyDashboardScopeData> result = loader().loadFamilyScope("family-1", null, true);

        assertThat(result).isPresent();
        List<ChildEntity> visible = result.get().visibleChildren();
        assertThat(visible).extracting(ChildEntity::getId).containsExactly(10);
    }

    @Test
    void adminSession_returnsEmptyScopeWhenAllChildrenInactive() {
        when(familyRepository.getDbId("family-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("family-1")).thenReturn(Optional.empty());
        when(familyRepository.getLastSelectedChildId("family-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(
            child(11, "Петя", ChildStatus.INACTIVE.name())
        ));

        Optional<FamilyDashboardScopeData> result = loader().loadFamilyScope("family-1", null, true);

        assertThat(result).isPresent();
        assertThat(result.get().activeChild()).isNull();
        assertThat(result.get().visibleChildren()).isEmpty();
    }

    @Test
    void childSession_keepsRequestedChildRegardlessOfStatus() {
        when(familyRepository.getDbId("family-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("family-1")).thenReturn(Optional.empty());
        when(familyRepository.getLastSelectedChildId("family-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(
            child(11, "Петя", ChildStatus.INACTIVE.name())
        ));

        Optional<FamilyDashboardScopeData> result = loader().loadFamilyScope("family-1", 11, false);

        assertThat(result).isPresent();
        assertThat(result.get().activeChild()).isNotNull();
        assertThat(result.get().activeChild().getId()).isEqualTo(11);
    }
}
