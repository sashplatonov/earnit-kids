package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.CreatedAtEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EntityTimestampsTest {
    private static final Instant SENTINEL_CREATED_AT = Instant.parse("1999-01-01T00:00:00Z");
    private static final Instant SENTINEL_UPDATED_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Inject FamilyRepository familyRepository;
    @Inject EntityManager entityManager;

    @Test
    @Transactional
    void createdAtRemainsStableWhileUpdatedAtRefreshesOnEntityUpdate() throws Exception {
        String familyId = "fam_timestamp_" + System.nanoTime();
        String email = familyId + "@test.com";

        FamilyEntity family = familyRepository.create(familyId, email, "secret123", true, null)
            .orElseThrow();

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

    private void overwriteCreatedAt(FamilyEntity entity, Instant createdAt) throws Exception {
        Field createdAtField = CreatedAtEntity.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(entity, createdAt);
    }
}