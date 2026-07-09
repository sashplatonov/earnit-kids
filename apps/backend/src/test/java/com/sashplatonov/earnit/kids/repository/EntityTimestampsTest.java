package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.CreatedAtEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EntityTimestampsTest {
    private static final Instant SENTINEL_CREATED_AT = Instant.parse("1999-01-01T00:00:00Z");
    private static final Instant SENTINEL_UPDATED_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Inject FamilyRepository familyRepository;
    @Inject ChildRepository childRepository;
    @Inject HistoryRepository historyRepository;
    @Inject EntityManager entityManager;

    @Test
    @Transactional
    void createdAtRemainsStableWhileUpdatedAtRefreshesOnEntityUpdate() throws Exception {
        String familyId = "fam_timestamp_" + System.nanoTime();
        String email = familyId + "@test.com";

        FamilyEntity family = familyRepository.create(familyId, email, "secret123", true, null)
            .orElseThrow();

        // Flush and reload so createdAt goes through H2 rounding (microsecond precision)
        entityManager.flush();
        entityManager.clear();
        family = familyRepository.findByDbId(family.getId()).orElseThrow();

        Instant originalCreatedAt = family.getCreatedAt();
        assertThat(originalCreatedAt).isNotNull();
        assertThat(family.getUpdatedAt()).isNotNull();

        entityManager.createNativeQuery(
            "UPDATE EARNIT_KIDS.families SET updated_at = TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00+00:00' WHERE id = ?")
            .setParameter(1, family.getId())
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        FamilyEntity managedFamily = familyRepository.findByDbId(family.getId()).orElseThrow();
        overwriteCreatedAt(managedFamily, SENTINEL_CREATED_AT);
        managedFamily.setAdminPassword("secret456");
        entityManager.flush();
        entityManager.clear();

        FamilyEntity reloadedFamily = familyRepository.findByDbId(family.getId()).orElseThrow();
        assertThat(reloadedFamily.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(reloadedFamily.getUpdatedAt()).isAfter(SENTINEL_UPDATED_AT);
    }

    @Test
    @Transactional
    void persistPreservesPresetCreatedAt() {
        String familyId = "fam_preset_timestamp_" + System.nanoTime();
        String email = familyId + "@test.com";

        FamilyEntity family = FamilyEntity.builder()
            .familyId(familyId)
            .email(email)
            .adminPassword("secret123")
            .verified(true)
            .createdAt(SENTINEL_CREATED_AT)
            .build();

        entityManager.persist(family);
        entityManager.flush();
        entityManager.clear();

        FamilyEntity reloadedFamily = familyRepository.findByDbId(family.getId()).orElseThrow();
        assertThat(reloadedFamily.getCreatedAt()).isEqualTo(SENTINEL_CREATED_AT);
        assertThat(reloadedFamily.getUpdatedAt()).isEqualTo(SENTINEL_CREATED_AT);
    }

    @Test
    @Transactional
    void replaceHistory_preservesOldCreatedAtWhenAppendingNewEntry() {
        String familyId = "fam_history_rewrite_" + System.nanoTime();
        String email = familyId + "@test.com";

        FamilyEntity family = familyRepository.create(familyId, email, "secret123", true, null)
            .orElseThrow();
        var child = childRepository.createChild(family.getId(), "Alice").orElseThrow();

        Instant oldCreatedAtOne = Instant.parse("2026-04-10T08:00:00Z");
        Instant oldCreatedAtTwo = Instant.parse("2026-04-11T09:30:00Z");
        Instant newCreatedAt = Instant.parse("2026-04-21T10:45:00Z");

        historyRepository.replaceHistory(family.getId(), child.getId(), List.of(
            historyEntry(family.getId(), child.getId(), 1001L, 5, "Read", oldCreatedAtOne),
            historyEntry(family.getId(), child.getId(), 1002L, 7, "Math", oldCreatedAtTwo)
        ));
        entityManager.flush();
        entityManager.clear();

        historyRepository.replaceHistory(family.getId(), child.getId(), List.of(
            historyEntry(family.getId(), child.getId(), 1001L, 5, "Read", oldCreatedAtOne),
            historyEntry(family.getId(), child.getId(), 1002L, 7, "Math", oldCreatedAtTwo),
            historyEntry(family.getId(), child.getId(), 1003L, 9, "Puzzle", newCreatedAt)
        ));
        entityManager.flush();
        entityManager.clear();

        List<HistoryEntryEntity> history = historyRepository.getHistoryForFamily(family.getId(), 10, 0);
        assertThat(history)
            .extracting(HistoryEntryEntity::getExternalId, HistoryEntryEntity::getCreatedAt)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(1001L, oldCreatedAtOne),
                org.assertj.core.groups.Tuple.tuple(1002L, oldCreatedAtTwo),
                org.assertj.core.groups.Tuple.tuple(1003L, newCreatedAt)
            );
    }

    private void overwriteCreatedAt(FamilyEntity entity, Instant createdAt) throws Exception {
        Field createdAtField = CreatedAtEntity.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(entity, createdAt);
    }

    private HistoryEntryEntity historyEntry(int familyDbId, int childId, long externalId,
                                            int amount, String description, Instant createdAt) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(externalId)
            .type(com.sashplatonov.earnit.kids.domain.model.HistoryEntryType.earn)
            .amount(amount)
            .description(description)
            .createdAt(createdAt)
            .build();
    }
}
