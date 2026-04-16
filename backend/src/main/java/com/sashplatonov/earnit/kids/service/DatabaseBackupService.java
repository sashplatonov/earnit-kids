package com.sashplatonov.earnit.kids.service;

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
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DatabaseBackupService {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    @Inject
    public DatabaseBackupService(
        @ConfigProperty(name = "quarkus.datasource.jdbc.url") String jdbcUrl,
        @ConfigProperty(name = "quarkus.datasource.username") String username,
        @ConfigProperty(name = "quarkus.datasource.password") Optional<String> password
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password.orElse("");
    }

    public OperationResult<BackupArtifact> createBackup() {
        try {
            PostgresConnectionDetails connection = PostgresConnectionDetails.fromJdbcUrl(jdbcUrl);
            Path backupDir = Path.of("data", "backups");
            Files.createDirectories(backupDir);
            String filename = "earnit-kids-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now()) + ".dump";
            Path dumpFile = backupDir.resolve(filename);

            ProcessBuilder builder = new ProcessBuilder(buildPgDumpCommand(connection, dumpFile));
            builder.environment().put("PGPASSWORD", password);
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("pg_dump failed: {}", stderr);
                return OperationResult.failure(stderr.isBlank() ? "pg_dump завершился с ошибкой" : stderr.trim());
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

        try {
            PostgresConnectionDetails connection = PostgresConnectionDetails.fromJdbcUrl(jdbcUrl);
            Path tempFile = Files.createTempFile("earnit-restore-", ".dump");
            Files.write(tempFile, payload);

            ProcessBuilder builder = new ProcessBuilder(buildPgRestoreCommand(connection, tempFile));
            builder.environment().put("PGPASSWORD", password);
            Process process = builder.start();
            int exitCode = process.waitFor();
            Files.deleteIfExists(tempFile);
            if (exitCode != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("pg_restore failed: {}", stderr);
                return OperationResult.failure(stderr.isBlank() ? "pg_restore завершился с ошибкой" : stderr.trim());
            }
            return OperationResult.success(null);
        } catch (IOException ex) {
            log.error("Backup restore failed", ex);
            return OperationResult.failure(ex.getMessage().contains("No such file")
                ? "pg_restore не найден в окружении"
                : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OperationResult.failure("Восстановление базы было прервано");
        } catch (IllegalArgumentException ex) {
            return OperationResult.failure(ex.getMessage());
        }
    }

    private List<String> buildPgDumpCommand(PostgresConnectionDetails connection, Path dumpFile) {
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("--format=custom");
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

    private List<String> buildPgRestoreCommand(PostgresConnectionDetails connection, Path dumpFile) {
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("--clean");
        command.add("--if-exists");
        command.add("--no-owner");
        command.add("--no-privileges");
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
