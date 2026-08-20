package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class FamilyOperationGuard {

    private final Supplier<FamilyRepository> familyRepository;

    public FamilyOperationGuard(FamilyRepository familyRepository) {
        this.familyRepository = () -> familyRepository;
    }

    public OperationResult<Integer> requireFamilyDbId(String familyId) {
        Optional<Integer> dbId = familyRepository.get().getDbId(familyId);
        return dbId.<OperationResult<Integer>>map(OperationResult::success)
            .orElseGet(() -> ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound"));
    }
}
