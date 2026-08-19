package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;

import java.util.Objects;
import java.util.Optional;

// EXPLAIN: Resolves a child only when it belongs to the given family, so callers never operate on a child from another family.
public class ChildOwnershipService {

    private final ChildRepository childRepository;

    public ChildOwnershipService(ChildRepository childRepository) {
        this.childRepository = childRepository;
    }

    public Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }
}
