package com.sashplatonov.earnit.kids.telegram.infrastructure.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TelegramWebhookUpdateRepositoryTest {
    @Inject TelegramWebhookUpdateRepository repository;

    @Test
    @Transactional
    void recordIfNew_insertsOnlyTheFirstOccurrence() {
        long updateId = System.nanoTime();
        Instant receivedAt = Instant.parse("2026-08-13T12:00:00Z");

        assertThat(repository.recordIfNew(updateId, receivedAt)).isTrue();
        assertThat(repository.recordIfNew(updateId, receivedAt)).isFalse();
    }
}
