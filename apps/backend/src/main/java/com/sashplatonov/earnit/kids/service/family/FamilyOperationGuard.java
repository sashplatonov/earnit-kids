package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.function.Supplier;

// EXPLAIN: Resolves a family's numeric DB id from its string familyId, returning a FAMILY_NOT_FOUND failure when absent so call sites collapse the guard to one line.
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
