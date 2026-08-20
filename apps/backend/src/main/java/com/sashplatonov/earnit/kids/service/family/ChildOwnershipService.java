package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
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
