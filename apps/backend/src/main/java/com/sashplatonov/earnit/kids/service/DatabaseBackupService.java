package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.dto.response.BackupHistoryItemResponse;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@ApplicationScoped
public class DatabaseBackupService {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schemaName;
    private final Path backupDir;
    private final TimeProvider timeProvider;
    private final DatabaseCommandRunner commandRunner;
    private final BackupTelegramSettingsService backupTelegramSettingsService;

    @Inject
    public DatabaseBackupService(
        @ConfigProperty(name = "quarkus.datasource.jdbc.url") String jdbcUrl,
        @ConfigProperty(name = "quarkus.datasource.username") String username,
        @ConfigProperty(name = "quarkus.datasource.password") Optional<String> password,
        @ConfigProperty(name = "DB_SCHEMA", defaultValue = "earnit_kids") String schemaName,
        @ConfigProperty(name = "app.backup.dir", defaultValue = "data/backups") String backupDir,
        TimeProvider timeProvider,
        DatabaseCommandRunner commandRunner,
        BackupTelegramSettingsService backupTelegramSettingsService
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password.orElse("");
        this.schemaName = schemaName;
        this.backupDir = Path.of(backupDir);
        this.timeProvider = timeProvider;
        this.commandRunner = commandRunner;
        this.backupTelegramSettingsService = backupTelegramSettingsService;
    }

    public OperationResult<BackupArtifact> createBackup() {
        try {
            PostgresConnectionDetails connection = PostgresConnectionDetails.fromJdbcUrl(jdbcUrl);
            Files.createDirectories(backupDir);
            String filename = "earnit-kids-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(timeProvider.now()) + ".dump";
            Path dumpFile = backupDir.resolve(filename);

            DatabaseCommandResult result = commandRunner.run(buildPgDumpCommand(connection, dumpFile), password);
            if (result.exitCode() != 0) {
                String stderr = normalizeError(result.stderr(), BackendMessages.message("backup.pgDumpFailed"));
                log.error("pg_dump failed: {}", stderr);
                return OperationResult.failure(stderr);
            }
            pruneOldBackups();
            return OperationResult.success(new BackupArtifact(dumpFile, filename));
        } catch (IOException ex) {
            log.error("Backup creation failed", ex);
            return OperationResult.failure(ex.getMessage().contains("No such file")
                ? BackendMessages.message("backup.pgDumpMissing")
                : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OperationResult.failure(BackendMessages.message("backup.backupInterrupted"));
        } catch (IllegalArgumentException ex) {
            return OperationResult.failure(ex.getMessage());
        }
    }

    public List<BackupHistoryItemResponse> listBackups() {
        try {
            if (!Files.exists(backupDir)) {
                return List.of();
            }
            try (Stream<Path> files = Files.list(backupDir)) {
                return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dump"))
                    .sorted(Comparator.comparing(this::safeLastModified).reversed())
                    .map(this::toHistoryItem)
                    .toList();
            }
        } catch (IOException ex) {
            log.error("Failed to list backups", ex);
            return List.of();
        }
    }

    public OperationResult<Void> restoreBackup(String filename) {
        if (filename == null || filename.isBlank()) {
            return OperationResult.failure(BackendMessages.message("backup.fileNotFound"));
        }
        Path backupFile = resolveBackupFile(filename);
        if (!Files.isRegularFile(backupFile)) {
            return OperationResult.failure(BackendMessages.message("backup.fileNotFound"));
        }
        try {
            return restoreBackup(Files.readAllBytes(backupFile));
        } catch (IOException ex) {
            log.error("Backup restore from file failed", ex);
            return OperationResult.failure(ex.getMessage());
        }
    }

    public OperationResult<Void> restoreBackup(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return OperationResult.failure(BackendMessages.message("backup.emptyFile"));
        }

        Path tempFile = null;
        Path tocFile = null;
        try {
            PostgresConnectionDetails connection = PostgresConnectionDetails.fromJdbcUrl(jdbcUrl);
            tempFile = Files.createTempFile("earnit-restore-", ".dump");
            Files.write(tempFile, payload);

            tocFile = buildSchemaScopedRestoreList(tempFile);

            DatabaseCommandResult resetSchemaResult = commandRunner.run(buildSchemaResetCommand(connection), password);
            if (resetSchemaResult.exitCode() != 0) {
                String stderr = normalizeError(resetSchemaResult.stderr(), BackendMessages.message("backup.schemaResetFailed"));
                log.error("psql schema reset failed: {}", stderr);
                return OperationResult.failure(stderr);
            }

            DatabaseCommandResult restoreResult = commandRunner.run(buildPgRestoreCommand(connection, tempFile, tocFile), password);
            if (restoreResult.exitCode() != 0) {
                String stderr = normalizeError(restoreResult.stderr(), BackendMessages.message("backup.pgRestoreFailed"));
                log.error("pg_restore failed: {}", stderr);
                return OperationResult.failure(stderr);
            }
            return OperationResult.success(null);
        } catch (IOException ex) {
            log.error("Backup restore failed", ex);
            return OperationResult.failure(ex.getMessage().contains("No such file")
                ? BackendMessages.message("backup.pgCliMissing")
                : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OperationResult.failure(BackendMessages.message("backup.restoreInterrupted"));
        } catch (IllegalArgumentException ex) {
            return OperationResult.failure(ex.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.warn("Не удалось удалить временный файл restore: {}", tempFile, ex);
                }
            }
            if (tocFile != null) {
                try {
                    Files.deleteIfExists(tocFile);
                } catch (IOException ex) {
                    log.warn("Не удалось удалить временный TOC файл restore: {}", tocFile, ex);
                }
            }
        }
    }

    private List<String> buildPgDumpCommand(PostgresConnectionDetails connection, Path dumpFile) {
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("--format=custom");
        command.add("--schema");
        command.add(schemaName);
        command.add("--file");
        command.add(dumpFile.toString());
        command.add("--host");
        command.add(connection.host());
        command.add("--port");
        command.add(String.valueOf(connection.port()));
        command.add("--username");
        command.add(username);
        command.add(connection.database());
        return command;
    }

    private List<String> buildSchemaResetCommand(PostgresConnectionDetails connection) {
        List<String> command = new ArrayList<>();
        command.add("psql");
        command.add("--set");
        command.add("ON_ERROR_STOP=1");
        command.add("--host");
        command.add(connection.host());
        command.add("--port");
        command.add(String.valueOf(connection.port()));
        command.add("--username");
        command.add(username);
        command.add("--dbname");
        command.add(connection.database());
        command.add("--command");
        command.add(buildSchemaResetSql());
        return command;
    }

    private List<String> buildPgRestoreCommand(PostgresConnectionDetails connection, Path dumpFile, Path tocFile) {
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("--exit-on-error");
        command.add("--single-transaction");
        command.add("--no-owner");
        command.add("--no-privileges");
        command.add("--schema");
        command.add(schemaName);
        command.add("--use-list");
        command.add(tocFile.toString());
        command.add("--host");
        command.add(connection.host());
        command.add("--port");
        command.add(String.valueOf(connection.port()));
        command.add("--username");
        command.add(username);
        command.add("--dbname");
        command.add(connection.database());
        command.add(dumpFile.toString());
        return command;
    }

    private Path buildSchemaScopedRestoreList(Path dumpFile) throws IOException, InterruptedException {
        DatabaseCommandResult listResult = commandRunner.run(buildPgRestoreListCommand(dumpFile), password);
        if (listResult.exitCode() != 0) {
            String stderr = normalizeError(listResult.stderr(), BackendMessages.message("backup.pgRestoreFailed"));
            throw new IOException(stderr);
        }

        List<String> filteredLines = filterRestoreList(listResult.stdout());
        if (filteredLines.stream().noneMatch(line -> !line.startsWith(";") && !line.isBlank())) {
            throw new IOException(BackendMessages.message("backup.schemaNotFoundInDump"));
        }

        Path tocFile = Files.createTempFile("earnit-restore-list-", ".toc");
        Files.writeString(tocFile, String.join(System.lineSeparator(), filteredLines), StandardCharsets.UTF_8);
        return tocFile;
    }

    private List<String> buildPgRestoreListCommand(Path dumpFile) {
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("--list");
        command.add(dumpFile.toString());
        return command;
    }

    private List<String> filterRestoreList(String tocContent) {
        Pattern schemaPattern = Pattern.compile("(^|\\s)" + Pattern.quote(schemaName) + "(\\s|$)");
        return tocContent.lines()
            .filter(line -> line.startsWith(";") || line.isBlank() || schemaPattern.matcher(line).find())
            .toList();
    }

    private String buildSchemaResetSql() {
        String quotedSchemaName = '"' + schemaName.replace("\"", "\"\"") + '"';
        return "DROP SCHEMA IF EXISTS " + quotedSchemaName + " CASCADE; CREATE SCHEMA " + quotedSchemaName + ';';
    }

    private String normalizeError(String stderr, String fallbackMessage) {
        return stderr == null || stderr.isBlank() ? fallbackMessage : stderr.trim();
    }

    private void pruneOldBackups() {
        int retentionCount = backupTelegramSettingsService.currentSettings().backupRetentionCount();
        List<Path> backups = listBackupPaths();
        for (int i = retentionCount; i < backups.size(); i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (IOException ex) {
                log.warn("Failed to delete old backup {}", backups.get(i), ex);
            }
        }
    }

    private List<Path> listBackupPaths() {
        try {
            if (!Files.exists(backupDir)) {
                return List.of();
            }
            try (Stream<Path> files = Files.list(backupDir)) {
                return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".dump"))
                    .sorted(Comparator.comparing(this::safeLastModified).reversed())
                    .toList();
            }
        } catch (IOException ex) {
            log.warn("Failed to inspect backup directory {}", backupDir, ex);
            return List.of();
        }
    }

    private BackupHistoryItemResponse toHistoryItem(Path path) {
        try {
            return new BackupHistoryItemResponse(
                path.getFileName().toString(),
                Files.size(path),
                Files.getLastModifiedTime(path).toInstant()
            );
        } catch (IOException ex) {
            return new BackupHistoryItemResponse(path.getFileName().toString(), 0L, null);
        }
    }

    private Instant safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private Path resolveBackupFile(String filename) {
        String normalized = Path.of(filename).getFileName().toString();
        return backupDir.resolve(normalized);
    }

    public record BackupArtifact(Path path, String filename) {
    }

    private record PostgresConnectionDetails(String host, int port, String database) {
        private static final String JDBC_PREFIX = "jdbc:postgresql:" + "/" + "/";

        private static PostgresConnectionDetails fromJdbcUrl(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX)) {
                throw new IllegalArgumentException(BackendMessages.message("backup.postgresOnly"));
            }

            String withoutPrefix = jdbcUrl.substring(JDBC_PREFIX.length());
            String hostAndPath = withoutPrefix.split("\\?", 2)[0];
            String[] hostParts = hostAndPath.split("/", 2);
            if (hostParts.length != 2) {
                throw new IllegalArgumentException(BackendMessages.message("backup.invalidJdbcUrl"));
            }

            String hostPort = hostParts[0];
            String database = hostParts[1];
            String[] hostPortParts = hostPort.split(":", 2);
            String host = hostPortParts[0];
            int port = hostPortParts.length == 2 ? Integer.parseInt(hostPortParts[1]) : 5432;
            return new PostgresConnectionDetails(host, port, database);
        }
    }
}
