package com.sashplatonov.earnit.kids.platform.domain.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreatedAtEntityTest {

    @Test
    void onCreate_setsCreatedAtAndUpdatedAtToSameInstant() {
        TestCreatedAtEntity entity = new TestCreatedAtEntity();

        entity.markCreated();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void onCreate_preservesPresetCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-10T12:30:00Z");
        TestCreatedAtEntity entity = new TestCreatedAtEntity();
        entity.seed(createdAt, null);

        entity.markCreated();

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    void onUpdate_refreshesUpdatedAtWithoutChangingCreatedAt() {
        Instant createdAt = Instant.parse("2026-04-20T00:00:00Z");
        Instant previousUpdatedAt = Instant.EPOCH;
        TestCreatedAtEntity entity = new TestCreatedAtEntity();
        entity.seed(createdAt, previousUpdatedAt);

        entity.markUpdated();

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isAfter(previousUpdatedAt);
    }

    private static final class TestCreatedAtEntity extends CreatedAtEntity {

        void markCreated() {
            onCreate();
        }

        void markUpdated() {
            onUpdate();
        }

        void seed(Instant createdAt, Instant updatedAt) {
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}