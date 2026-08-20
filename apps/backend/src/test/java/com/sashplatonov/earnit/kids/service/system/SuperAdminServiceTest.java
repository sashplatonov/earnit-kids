package com.sashplatonov.earnit.kids.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.auth.PasswordHasher;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.database.BaseDataService;
import com.sashplatonov.earnit.kids.service.family.FamilyOperationGuard;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.observability.BackendKpiMetrics;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SuperAdminServiceTest {
    private FamilyRepository families;
    private ChildRepository children;
    private FamilyOperationGuard guard;
    private FamilyService familyService;
    private PasswordHasher hasher;
    private SuperAdminService service;

    @BeforeEach
    void setUp() {
        families = mock(FamilyRepository.class);
        children = mock(ChildRepository.class);
        guard = mock(FamilyOperationGuard.class);
        familyService = mock(FamilyService.class);
        hasher = mock(PasswordHasher.class);
        service = new SuperAdminService(
            families, children, mock(TaskRepository.class), mock(HistoryRepository.class),
            mock(PurchaseRequestRepository.class), familyService, mock(BaseDataService.class),
            new BackendKpiMetrics(new SimpleMeterRegistry()), new ObjectMapper(), hasher, guard);
    }

    @Test
    void familyQueries_coverPresentAndMissingBranches() {
        FamilyEntity family = FamilyEntity.builder().id(7).familyId("fam-1").email("a@test.com").build();
        ChildEntity child = ChildEntity.builder().id(3).name("Alice").balance(12).monthlyLimit(100).build();
        when(families.listAll()).thenReturn(List.of(family));
        when(families.findById("fam-1")).thenReturn(Optional.of(family));
        when(children.getChildren(7)).thenReturn(List.of(child));

        assertThat(service.getFamilies().families()).hasSize(1);
        assertThat(service.getFamilyDetails("fam-1")).isNotNull();
        when(families.findById("missing")).thenReturn(Optional.empty());
        assertThat(service.getFamilyDetails("missing")).isNull();
    }

    @Test
    void passwordBranchesRejectWeakMissingAndReuse() {
        assertThat(service.setFamilyPassword("fam-1", "aaaaaa")).isInstanceOf(OperationResult.Failure.class);
        when(families.findById("fam-1")).thenReturn(Optional.empty());
        assertThat(service.setFamilyPassword("fam-1", "strong-password"))
            .isInstanceOf(OperationResult.Failure.class);

        FamilyEntity family = FamilyEntity.builder().id(7).familyId("fam-1").adminPassword("same").build();
        when(families.findById("fam-1")).thenReturn(Optional.of(family));
        assertThat(service.setFamilyPassword("fam-1", "same")).isInstanceOf(OperationResult.Failure.class);

        family.setAdminPassword("$argon2id$v=19$hash");
        when(hasher.isArgon2Hash(family.getAdminPassword())).thenReturn(true);
        when(hasher.verify(family.getAdminPassword(), "same")).thenReturn(true);
        assertThat(service.setFamilyPassword("fam-1", "same")).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void blockingAndTokenBranchesDelegateAndRejectInvalidState() {
        when(families.setBlocked("fam-1", true)).thenReturn(true);
        assertThat(service.setFamilyBlocked("fam-1", true)).isTrue();
        verify(families).updateLastActivity("fam-1");

        when(guard.requireFamilyDbId("fam-1"))
            .thenReturn(OperationResult.failure("FAMILY_NOT_FOUND", "missing"));
        assertThat(service.regenerateFamilyToken("fam-1")).isInstanceOf(OperationResult.Failure.class);
        when(guard.requireFamilyDbId("fam-2")).thenReturn(OperationResult.success(8));
        when(children.getChildren(8)).thenReturn(List.of());
        assertThat(service.regenerateFamilyToken("fam-2")).isInstanceOf(OperationResult.Failure.class);
        when(children.findByIdOptional(3)).thenReturn(Optional.empty());
        assertThat(service.regenerateChildToken(3)).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void tokenAndPasswordSuccessBranches_delegateToOwners() {
        FamilyEntity family = FamilyEntity.builder().id(7).familyId("fam-1").adminPassword("old").build();
        ChildEntity child = ChildEntity.builder().id(3).familyDbId(7).build();
        when(families.findById("fam-1")).thenReturn(Optional.of(family));
        when(hasher.isArgon2Hash("old")).thenReturn(false);
        when(hasher.verifyLegacy("strong-password", "old")).thenReturn(false);
        when(hasher.hash("strong-password")).thenReturn("hashed");
        when(families.updatePassword("fam-1", "hashed")).thenReturn(true);
        assertThat(service.setFamilyPassword("fam-1", "strong-password"))
            .isInstanceOf(OperationResult.Success.class);

        when(guard.requireFamilyDbId("fam-1")).thenReturn(OperationResult.success(7));
        when(children.getChildren(7)).thenReturn(List.of(child));
        when(familyService.regenerateChildToken("fam-1", 3)).thenReturn(OperationResult.success("token"));
        assertThat(service.regenerateFamilyToken("fam-1")).isEqualTo(OperationResult.success("token"));

        when(children.findByIdOptional(3)).thenReturn(Optional.of(child));
        when(families.findByDbId(child.getFamilyDbId())).thenReturn(Optional.of(family));
        when(familyService.regenerateChildToken("fam-1", 3)).thenReturn(OperationResult.success("token-2"));
        assertThat(service.regenerateChildToken(3)).isEqualTo(OperationResult.success("token-2"));
    }

    @Test
    void tokenRegeneration_reportsMissingFamily() {
        ChildEntity child = ChildEntity.builder().id(3).familyDbId(7).build();
        when(children.findByIdOptional(3)).thenReturn(Optional.of(child));
        when(families.findByDbId(child.getFamilyDbId())).thenReturn(Optional.empty());
        assertThat(service.regenerateChildToken(3)).isInstanceOf(OperationResult.Failure.class);
    }
}
