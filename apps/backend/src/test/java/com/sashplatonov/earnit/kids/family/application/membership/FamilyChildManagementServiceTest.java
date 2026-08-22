package com.sashplatonov.earnit.kids.family.application.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.api.request.ChildTheme;
import com.sashplatonov.earnit.kids.family.api.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FamilyChildManagementServiceTest {
    private ChildRepository children;
    private FamilyOperationGuard guard;
    private ChildOwnershipService ownership;
    private FamilyChildManagementService service;

    @BeforeEach
    void setUp() {
        children = mock(ChildRepository.class);
        guard = mock(FamilyOperationGuard.class);
        ownership = mock(ChildOwnershipService.class);
        service = new FamilyChildManagementService(
            children, new ObjectMapper(), mock(AnalyticsService.class), guard, ownership);
    }

    @Test
    void createChild_validatesAndCreates() {
        when(guard.requireFamilyDbId("fam-1")).thenReturn(OperationResult.success(7));
        ChildEntity child = ChildEntity.builder().id(3).name("Alice").token("token").build();
        when(children.createChild(7, "Alice")).thenReturn(Optional.of(child));

        assertThat(service.createChild("fam-1", "Alice")).isInstanceOf(OperationResult.Success.class);
        when(children.isNicknameTaken(7, "Alice", null)).thenReturn(true);
        assertThat(service.createChild("fam-1", "Alice")).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void deleteAndRenameRejectForeignOrInvalidChildren() {
        when(guard.requireFamilyDbId("fam-1")).thenReturn(OperationResult.success(7));
        when(children.findByIdOptional(3)).thenReturn(Optional.empty());
        assertThat(service.deleteChild("fam-1", 3)).isInstanceOf(OperationResult.Failure.class);

        ChildEntity child = ChildEntity.builder().id(3).familyDbId(7).name("Old").build();
        when(children.findByIdOptional(3)).thenReturn(Optional.of(child));
        when(ownership.findFamilyChild(7, 3)).thenReturn(Optional.of(child));
        assertThat(service.updateNickname("fam-1", 3, " ")).isInstanceOf(OperationResult.Failure.class);
        when(children.isNicknameTaken(7, "New", 3)).thenReturn(false);
        assertThat(service.updateNickname("fam-1", 3, "New")).isInstanceOf(OperationResult.Success.class);
        verify(children).updateName(3, "New");
    }

    @Test
    void settingsThemeOrderAndStatus_delegateAfterOwnershipCheck() {
        when(guard.requireFamilyDbId("fam-1")).thenReturn(OperationResult.success(7));
        ChildEntity child = ChildEntity.builder().id(3).familyDbId(7).dailyRewardLimit(4).build();
        when(ownership.findFamilyChild(7, 3)).thenReturn(Optional.of(child));

        assertThat(service.updateChildSettings("fam-1", 3, "Alice", 10, 20, null))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updateChildTheme("fam-1", 3, ChildTheme.ocean))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updateChildGroupOrder("fam-1", 3, GroupOrderSection.tasks,
            java.util.List.of("Home"), java.util.List.of("Hidden"), false))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.setChildActive("fam-1", 3, false)).isInstanceOf(OperationResult.Success.class);
        verify(children).updateSettings(3, "Alice", 10, 20, 4);
        verify(children).updateTheme(3, ChildTheme.ocean);
        verify(children).updateStatus(3, "INACTIVE");
    }
}
