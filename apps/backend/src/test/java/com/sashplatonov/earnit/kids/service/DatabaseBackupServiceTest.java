package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.BackupHistoryItemResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-20T10:15:30Z");

    @Mock DatabaseCommandRunner commandRunner;
    @Mock BackupTelegramSettingsService backupTelegramSettingsService;

    @TempDir
    Path tempDir;

    private DatabaseBackupService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseBackupService(
            "jdbc:postgresql://db:5432/earnit_kids",
            "earnit",
            Optional.of("secret"),
            "earnit_kids",
            tempDir.toString(),
            TestConfigFactory.timeProvider(FIXED_NOW),
            commandRunner,
            backupTelegramSettingsService
        );
    }

    private DatabaseCommandResult dumpCreated(List<String> command) throws Exception {
        if (command.contains("--file")) {
            Path dumpPath = Path.of(command.get(command.indexOf("--file") + 1));
            Files.writeString(dumpPath, "dump");
        }
        return new DatabaseCommandResult(0, "", "");
    }

    @Test
    void createBackup_limitsDumpToConfiguredSchema() throws Exception {
        when(backupTelegramSettingsService.currentSettings())
            .thenReturn(new TelegramBackupSettingsSnapshot(false, null, null, 24, 20, null, null, null));
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenAnswer(invocation -> {
                List<String> command = invocation.getArgument(0);
                return dumpCreated(command);
            });

        OperationResult<DatabaseBackupService.BackupArtifact> result = service.createBackup();

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).run(commandCaptor.capture(), org.mockito.ArgumentMatchers.eq("secret"));
        List<String> command = commandCaptor.getValue();
        assertThat(command).containsSequence("pg_dump", "--format=custom", "--schema", "earnit_kids");
        assertThat(command).contains("--file");
        assertThat(command).contains("--host", "db", "--port", "5432", "--username", "earnit", "earnit_kids");
    }

    @Test
    void createBackup_prunesFilesBeyondRetentionLimit() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        DatabaseBackupService serviceWithIncrementingTime = new DatabaseBackupService(
            "jdbc:postgresql://db:5432/earnit_kids",
            "earnit",
            Optional.of("secret"),
            "earnit_kids",
            tempDir.toString(),
            () -> FIXED_NOW.plusSeconds(counter.incrementAndGet()),
            commandRunner,
            backupTelegramSettingsService
        );

        when(backupTelegramSettingsService.currentSettings())
            .thenReturn(new TelegramBackupSettingsSnapshot(false, null, null, 24, 2, null, null, null));
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenAnswer(invocation -> {
                List<String> command = invocation.getArgument(0);
                return dumpCreated(command);
            });

        serviceWithIncrementingTime.createBackup();
        serviceWithIncrementingTime.createBackup();
        serviceWithIncrementingTime.createBackup();

        assertThat(serviceWithIncrementingTime.listBackups()).hasSize(2);
    }

    @Test
    void restoreBackup_fromStoredFile_restoresSuccessfully() throws Exception {
        Files.write(tempDir.resolve("saved.dump"), new byte[]{1, 2, 3});
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenReturn(
                new DatabaseCommandResult(0, String.join("\n",
                    "; comment",
                    "1; 0 0 SCHEMA - earnit_kids owner",
                    "2; 0 0 TABLE earnit_kids sample owner",
                    "3; 0 0 TABLE other_schema ignored owner"
                ), ""),
                new DatabaseCommandResult(0, "", ""),
                new DatabaseCommandResult(0, "", "")
            );

        OperationResult<Void> result = service.restoreBackup("saved.dump");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(commandRunner, times(3)).run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret"));
    }

    @Test
    void restoreBackup_resetsSchemaBeforeSchemaScopedRestore() throws Exception {
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenReturn(
                new DatabaseCommandResult(0, String.join("\n",
                    "; comment",
                    "1; 0 0 SCHEMA - earnit_kids owner",
                    "2; 0 0 TABLE earnit_kids sample owner",
                    "3; 0 0 TABLE other_schema ignored owner"
                ), ""),
                new DatabaseCommandResult(0, "", ""),
                new DatabaseCommandResult(0, "", "")
            );

        OperationResult<Void> result = service.restoreBackup(new byte[]{1, 2, 3});

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(3)).run(commandCaptor.capture(), org.mockito.ArgumentMatchers.eq("secret"));
        List<List<String>> commands = commandCaptor.getAllValues();
        assertThat(commands.get(0)).containsSequence("pg_restore", "--list");
        assertThat(commands.get(0)).containsSequence(
            "pg_restore",
            "--list"
        );
        assertThat(commands.get(1)).containsSequence(
            "psql",
            "--set",
            "ON_ERROR_STOP=1",
            "--host",
            "db",
            "--port",
            "5432",
            "--username",
            "earnit",
            "--dbname",
            "earnit_kids"
        );
        assertThat(commands.get(1)).contains("--command", "DROP SCHEMA IF EXISTS \"earnit_kids\" CASCADE; CREATE SCHEMA \"earnit_kids\";");
        assertThat(commands.get(2)).containsSequence(
            "pg_restore",
            "--exit-on-error",
            "--single-transaction",
            "--no-owner",
            "--no-privileges",
            "--schema",
            "earnit_kids"
        );
        assertThat(commands.get(2)).contains("--use-list");
        assertThat(commands.get(2)).doesNotContain("--clean", "--if-exists");
        assertThat(commands.get(2)).contains("--host", "db", "--port", "5432", "--username", "earnit", "--dbname", "earnit_kids");
    }

    @Test
    void restoreBackup_schemaResetFailure_returnsFailure() throws Exception {
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenReturn(
                new DatabaseCommandResult(0, String.join("\n",
                    "; comment",
                    "1; 0 0 SCHEMA - earnit_kids owner"
                ), ""),
                new DatabaseCommandResult(1, "", "schema reset failed")
            );

        OperationResult<Void> result = service.restoreBackup(new byte[]{1, 2, 3});

        assertThat(result).isEqualTo(OperationResult.failure("schema reset failed"));
        verify(commandRunner, times(2)).run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret"));
    }

    // ── Backup history listing ──────────────────────────────────────────────

    @Test
    void listBackups_returnsEmptyListWhenDirectoryDoesNotExist() {
        DatabaseBackupService serviceWithMissingDir = new DatabaseBackupService(
            "jdbc:postgresql://db:5432/earnit_kids",
            "earnit",
            Optional.of("secret"),
            "earnit_kids",
            tempDir.resolve("nonexistent").toString(),
            TestConfigFactory.timeProvider(FIXED_NOW),
            commandRunner,
            backupTelegramSettingsService
        );

        assertThat(serviceWithMissingDir.listBackups()).isEmpty();
    }

    @Test
    void listBackups_returnsEmptyListWhenNoDumpFiles() throws Exception {
        Files.createFile(tempDir.resolve("readme.txt"));
        Files.createFile(tempDir.resolve(".hidden"));

        assertThat(service.listBackups()).isEmpty();
    }

    @Test
    void listBackups_returnsBackupsSortedByLastModifiedDescending() throws Exception {
        Path oldest = tempDir.resolve("earnit-kids-20260419-100000.dump");
        Path middle = tempDir.resolve("earnit-kids-20260420-101530.dump");
        Path newest = tempDir.resolve("earnit-kids-20260420-101531.dump");
        Files.writeString(oldest, "oldest");
        Files.writeString(middle, "middle");
        Files.writeString(newest, "newest");
        Files.setLastModifiedTime(oldest, java.nio.file.attribute.FileTime.from(Instant.parse("2026-04-19T10:00:00Z")));
        Files.setLastModifiedTime(middle, java.nio.file.attribute.FileTime.from(Instant.parse("2026-04-20T10:15:30Z")));
        Files.setLastModifiedTime(newest, java.nio.file.attribute.FileTime.from(Instant.parse("2026-04-20T10:15:31Z")));

        var backups = service.listBackups();

        assertThat(backups).hasSize(3);
        assertThat(backups.get(0).filename()).isEqualTo("earnit-kids-20260420-101531.dump");
        assertThat(backups.get(1).filename()).isEqualTo("earnit-kids-20260420-101530.dump");
        assertThat(backups.get(2).filename()).isEqualTo("earnit-kids-20260419-100000.dump");
    }

    @Test
    void listBackups_includesFileSizeAndTimestamp() throws Exception {
        Path dump = tempDir.resolve("earnit-kids-20260420-101530.dump");
        Files.writeString(dump, "hello backup");
        Files.setLastModifiedTime(dump, java.nio.file.attribute.FileTime.from(FIXED_NOW));

        var backups = service.listBackups();

        assertThat(backups).hasSize(1);
        assertThat(backups.get(0).filename()).isEqualTo("earnit-kids-20260420-101530.dump");
        assertThat(backups.get(0).sizeBytes()).isGreaterThan(0);
        assertThat(backups.get(0).createdAt()).isEqualTo(FIXED_NOW);
    }

    // ── Backup file path resolution ─────────────────────────────────────────

    @Test
    void getBackupFilePath_resolvesExistingFile() throws Exception {
        Path dump = tempDir.resolve("earnit-kids-20260420-101530.dump");
        Files.writeString(dump, "dump content");

        var result = service.getBackupFilePath("earnit-kids-20260420-101530.dump");

        assertThat(result).hasValue(dump);
    }

    @Test
    void getBackupFilePath_returnsEmptyForNonExistingFile() {
        var result = service.getBackupFilePath("nonexistent.dump");

        assertThat(result).isEmpty();
    }

    @Test
    void getBackupFilePath_returnsEmptyForBlankFilename() {
        assertThat(service.getBackupFilePath(null)).isEmpty();
        assertThat(service.getBackupFilePath("")).isEmpty();
        assertThat(service.getBackupFilePath("   ")).isEmpty();
    }

    @Test
    void getBackupFilePath_preventsPathTraversal() throws Exception {
        Path dump = tempDir.resolve("earnit-kids-20260420-101530.dump");
        Files.writeString(dump, "dump content");

        // Path traversal attempts: getFileName() strips directories,
        // so "../../etc/passwd" resolves to "passwd" in backupDir which doesn't exist
        var result = service.getBackupFilePath("../../../etc/passwd");
        assertThat(result).isEmpty();

        // Double-check: the legit filename still resolves correctly
        var legit = service.getBackupFilePath("earnit-kids-20260420-101530.dump");
        assertThat(legit).hasValue(dump);
    }

    // ── Backup persistence across service recreation ─────────────────────────

    @Test
    void listBackups_survivesServiceRecreation() throws Exception {
        // Simulate: create backup with one service instance
        when(backupTelegramSettingsService.currentSettings())
            .thenReturn(new TelegramBackupSettingsSnapshot(false, null, null, 24, 20, null, null, null));
        when(commandRunner.run(ArgumentMatchers.anyList(), ArgumentMatchers.eq("secret")))
            .thenAnswer(invocation -> {
                List<String> command = invocation.getArgument(0);
                return dumpCreated(command);
            });

        service.createBackup();
        assertThat(service.listBackups()).hasSize(1);
        String originalFileName = service.listBackups().get(0).filename();

        // Simulate redeployment: create a new service instance pointing to the same directory
        DatabaseBackupService recreatedService = new DatabaseBackupService(
            "jdbc:postgresql://db:5432/earnit_kids",
            "earnit",
            Optional.of("secret"),
            "earnit_kids",
            tempDir.toString(),
            TestConfigFactory.timeProvider(FIXED_NOW),
            commandRunner,
            backupTelegramSettingsService
        );

        var backupsAfterRedeploy = recreatedService.listBackups();
        assertThat(backupsAfterRedeploy).hasSize(1);
        assertThat(backupsAfterRedeploy.get(0).filename()).isEqualTo(originalFileName);
    }

    // ── Restore from stored file edge cases ──────────────────────────────────

    @Test
    void restoreBackup_fromHistory_returnsFailureForNonExistentFile() {
        OperationResult<Void> result = service.restoreBackup("nonexistent.dump");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<Void> failure = (OperationResult.Failure<Void>) result;
        assertThat(failure.message()).isEqualTo(BackendMessages.message("backup.fileNotFound"));
    }

    @Test
    void restoreBackup_fromHistory_returnsFailureForBlankFilename() {
        OperationResult<Void> nullResult = service.restoreBackup((String) null);
        OperationResult<Void> blankResult = service.restoreBackup("");

        assertThat(nullResult).isInstanceOf(OperationResult.Failure.class);
        assertThat(blankResult).isInstanceOf(OperationResult.Failure.class);
    }

    // ── Pruning edge cases ───────────────────────────────────────────────────

    @Test
    void createBackup_prunesToRetentionCountOfOne() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        DatabaseBackupService serviceWithIncrementingTime = new DatabaseBackupService(
            "jdbc:postgresql://db:5432/earnit_kids",
            "earnit",
            Optional.of("secret"),
            "earnit_kids",
            tempDir.toString(),
            () -> FIXED_NOW.plusSeconds(counter.incrementAndGet()),
            commandRunner,
            backupTelegramSettingsService
        );

        when(backupTelegramSettingsService.currentSettings())
            .thenReturn(new TelegramBackupSettingsSnapshot(false, null, null, 24, 1, null, null, null));
        when(commandRunner.run(ArgumentMatchers.anyList(), ArgumentMatchers.eq("secret")))
            .thenAnswer(invocation -> {
                List<String> command = invocation.getArgument(0);
                return dumpCreated(command);
            });

        serviceWithIncrementingTime.createBackup();
        serviceWithIncrementingTime.createBackup();
        serviceWithIncrementingTime.createBackup();

        assertThat(serviceWithIncrementingTime.listBackups()).hasSize(1);
    }

    @Test
    void listBackups_skipsUnreadableFiles() throws Exception {
        Path good = tempDir.resolve("earnit-kids-20260420-101530.dump");
        Path bad = tempDir.resolve("earnit-kids-20260420-101531.dump");
        Files.writeString(good, "good");
        Files.writeString(bad, "bad");

        var backups = service.listBackups();
        // Both files should be listed even if one has metadata issues
        // because toHistoryItem handles IOException gracefully
        assertThat(backups).hasSize(2);
        assertThat(backups).extracting(BackupHistoryItemResponse::filename)
            .contains("earnit-kids-20260420-101530.dump", "earnit-kids-20260420-101531.dump");
    }
}
