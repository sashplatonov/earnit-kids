package com.sashplatonov.earnit.kids.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramBackupSettingsSnapshotTest {

    @Test
    void dueAt_usesLastAttemptTimestamp() {
        TelegramBackupSettingsSnapshot snapshot = new TelegramBackupSettingsSnapshot(
            true,
            "token",
            "chat",
            12,
            Instant.parse("2026-04-21T00:00:00Z"),
            Instant.parse("2026-04-20T12:00:00Z"),
            null
        );

        assertThat(snapshot.dueAt(Instant.parse("2026-04-21T11:59:59Z"))).isFalse();
        assertThat(snapshot.dueAt(Instant.parse("2026-04-21T12:00:00Z"))).isTrue();
    }
}
