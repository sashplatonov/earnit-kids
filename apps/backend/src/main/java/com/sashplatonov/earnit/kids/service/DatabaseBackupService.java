package com.sashplatonov.earnit.kids.service;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DatabaseBackupService {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schemaName;
    private final TimeProvider timeProvider;
    private final DatabaseCommandRunner commandRunner;

    @Inject
    public DatabaseBackupService(
        @ConfigProperty(name = "quarkus.datasource.jdbc.url") String jdbcUrl,
        @ConfigProperty(name = "quarkus.datasource.username") String username,
        @ConfigProperty(name = "quarkus.datasource.password") Optional<String> password,
        @ConfigProperty(name = "DB_SCHEMA", defaultValue = "earnit_kids") String schemaName,
        TimeProvider timeProvider,
        DatabaseCommandRunner commandRunner
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password.orElse("");
        this.schemaName = schemaName;
        this.timeProvider = timeProvider;
        this.commandRunner = commandRunner;
    }

    public OperationResult<BackupArtifact> createBackup() {
        try {
            PostgresConnectionDetails connection = PostgresConnectionDetails.fromJdbcUrl(jdbcUrl);
            Path backupDir = Path.of("data", "backups");
            Files.createDirectories(backupDir);
            String filename = "earnit-kids-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(timeProvider.now()) + ".dump";
            Path dumpFile = backupDir.resolve(filename);

            DatabaseCommandResult result = commandRunner.run(buildPgDumpCommand(connection, dumpFile), password);
            if (result.exitCode() != 0) {
                String stderr = normalizeError(result.stderr(), "pg_dump завершился с ошибкой");
                log.error("pg_dump failed: {}", stderr);
                return OperationResult.failure(stderr);
            }
            return OperationResult.success(new BackupArtifact(dumpFile, filename));
        } catch (IOException ex) {
            log.error("Backup creation failed", ex);
            return OperationResult.failure(ex.getMessage().contains("No such file")
                ? "pg_dump не найден в окружении"
                : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OperationResult.failure("Создание бэкапа было прервано");
        } catch (IllegalArgumentException ex) {
            return OperationResult.failure(ex.getMessage());
        }
    }

    public OperationResult<Void> restoreBackup(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return OperationResult.failure("Файл бэкапа пустой");
        }

        Path tempFile = null;
        try {
            PostgresConnectionDetails connection = PostgresConnectionDetails.fromJdbcUrl(jdbcUrl);
            tempFile = Files.createTempFile("earnit-restore-", ".dump");
            Files.write(tempFile, payload);

            DatabaseCommandResult resetSchemaResult = commandRunner.run(buildSchemaResetCommand(connection), password);
            if (resetSchemaResult.exitCode() != 0) {
                String stderr = normalizeError(resetSchemaResult.stderr(), "Подготовка схемы к восстановлению завершилась с ошибкой");
                log.error("psql schema reset failed: {}", stderr);
                return OperationResult.failure(stderr);
            }

            DatabaseCommandResult restoreResult = commandRunner.run(buildPgRestoreCommand(connection, tempFile), password);
            if (restoreResult.exitCode() != 0) {
                String stderr = normalizeError(restoreResult.stderr(), "pg_restore завершился с ошибкой");
                log.error("pg_restore failed: {}", stderr);
                return OperationResult.failure(stderr);
            }
            return OperationResult.success(null);
        } catch (IOException ex) {
            log.error("Backup restore failed", ex);
            return OperationResult.failure(ex.getMessage().contains("No such file")
                ? "PostgreSQL CLI не найден в окружении"
                : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OperationResult.failure("Восстановление базы было прервано");
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

    private List<String> buildPgRestoreCommand(PostgresConnectionDetails connection, Path dumpFile) {
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("--exit-on-error");
        command.add("--single-transaction");
        command.add("--no-owner");
        command.add("--no-privileges");
        command.add("--schema");
        command.add(schemaName);
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

    private String buildSchemaResetSql() {
        String quotedSchemaName = '"' + schemaName.replace("\"", "\"\"") + '"';
        return "DROP SCHEMA IF EXISTS " + quotedSchemaName + " CASCADE; CREATE SCHEMA " + quotedSchemaName + ';';
    }

    private String normalizeError(String stderr, String fallbackMessage) {
        return stderr == null || stderr.isBlank() ? fallbackMessage : stderr.trim();
    }

    public record BackupArtifact(Path path, String filename) {
    }

    private record PostgresConnectionDetails(String host, int port, String database) {
        private static final String JDBC_PREFIX = "jdbc:postgresql:" + "/" + "/";

        private static PostgresConnectionDetails fromJdbcUrl(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX)) {
                throw new IllegalArgumentException("Поддерживается только PostgreSQL datasource");
            }

            String withoutPrefix = jdbcUrl.substring(JDBC_PREFIX.length());
            String hostAndPath = withoutPrefix.split("\\?", 2)[0];
            String[] hostParts = hostAndPath.split("/", 2);
            if (hostParts.length != 2) {
                throw new IllegalArgumentException("Некорректный PostgreSQL JDBC URL");
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
