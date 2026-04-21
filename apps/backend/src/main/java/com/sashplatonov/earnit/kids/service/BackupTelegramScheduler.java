package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BackupTelegramScheduler {

    private final BackupTelegramSettingsService backupTelegramSettingsService;
    private final DatabaseBackupService databaseBackupService;
    private final TelegramBackupService telegramBackupService;
    private final TimeProvider timeProvider;

    @Scheduled(every = "5m", delayed = "30s", concurrentExecution = ConcurrentExecution.SKIP)
    void sendScheduledBackup() {
        Instant now = timeProvider.now();
        TelegramBackupSettingsSnapshot settings = backupTelegramSettingsService.currentSettings();
        if (!settings.dueAt(now)) {
            return;
        }

        OperationResult<DatabaseBackupService.BackupArtifact> backup = databaseBackupService.createBackup();
        if (backup instanceof OperationResult.Failure<DatabaseBackupService.BackupArtifact> failure) {
            backupTelegramSettingsService.recordFailure(now, failure.message());
            log.error("Scheduled backup creation failed: {}", failure.message());
            return;
        }

        DatabaseBackupService.BackupArtifact artifact =
            ((OperationResult.Success<DatabaseBackupService.BackupArtifact>) backup).value();
        OperationResult<Void> sent = telegramBackupService.sendBackup(artifact.path(), artifact.filename());
        if (sent instanceof OperationResult.Success<?>) {
            log.info("Scheduled database backup sent to Telegram");
            return;
        }

        OperationResult.Failure<Void> failure = (OperationResult.Failure<Void>) sent;
        log.error("Scheduled backup send failed: {}", failure.message());
    }
}
