package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChildOwnershipServiceTest {

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final ChildOwnershipService service = new ChildOwnershipService(childRepository);

    private static ChildEntity child(int id, int familyDbId) {
        return ChildEntity.builder()
            .id(id)
            .familyDbId(familyDbId)
            .name("child")
            .build();
    }

    @Test
    void findFamilyChild_childInFamily_returnsChild() {
        ChildEntity child = child(5, 42);
        when(childRepository.findByIdOptional(5)).thenReturn(Optional.of(child));

        assertThat(service.findFamilyChild(42, 5)).contains(child);
    }

    @Test
    void findFamilyChild_childInDifferentFamily_returnsEmpty() {
        ChildEntity child = child(5, 99);
        when(childRepository.findByIdOptional(5)).thenReturn(Optional.of(child));

        assertThat(service.findFamilyChild(42, 5)).isEmpty();
    }

    @Test
    void findFamilyChild_childDoesNotExist_returnsEmpty() {
        when(childRepository.findByIdOptional(5)).thenReturn(Optional.empty());

        assertThat(service.findFamilyChild(42, 5)).isEmpty();
    }
}
