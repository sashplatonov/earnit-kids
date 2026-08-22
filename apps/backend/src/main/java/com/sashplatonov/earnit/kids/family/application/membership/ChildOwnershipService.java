package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class ChildOwnershipService {

    private final Supplier<ChildRepository> childRepository;

    public ChildOwnershipService(ChildRepository childRepository) {
        this.childRepository = () -> childRepository;
    }

    public Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.get().findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }
}
