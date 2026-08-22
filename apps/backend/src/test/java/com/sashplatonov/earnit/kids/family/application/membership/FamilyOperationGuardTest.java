package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FamilyOperationGuardTest {

    private final FamilyRepository familyRepository = mock(FamilyRepository.class);
    private final FamilyOperationGuard guard = new FamilyOperationGuard(familyRepository);

    @Test
    void requireFamilyDbId_existingFamily_returnsSuccessWithDbId() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(42));

        OperationResult<Integer> result = guard.requireFamilyDbId("fam-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(((OperationResult.Success<Integer>) result).value()).isEqualTo(42);
    }

    @Test
    void requireFamilyDbId_missingFamily_returnsFailureWithFamilyNotFound() {
        when(familyRepository.getDbId("fam-missing")).thenReturn(Optional.empty());

        OperationResult<Integer> result = guard.requireFamilyDbId("fam-missing");

        assertThat(result.isSuccess()).isFalse();
        OperationResult.Failure<Integer> failure = (OperationResult.Failure<Integer>) result;
        assertThat(failure.errorCode()).isEqualTo("FAMILY_NOT_FOUND");
        assertThat(failure.message()).isEqualTo(BackendMessages.message("family.familyNotFound"));
    }
}
