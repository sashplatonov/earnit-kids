package com.sashplatonov.earnit.kids.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.nio.file.Files;
import java.nio.file.Path;

@Readiness
@ApplicationScoped
public class BackupDirectoryReadinessCheck implements HealthCheck {

    private static final String CHECK_NAME = "backup-directory";

    private final Path backupDir;

    @Inject
    public BackupDirectoryReadinessCheck(
        @ConfigProperty(name = "app.backup.dir", defaultValue = "data/backups") String backupDir
    ) {
        this.backupDir = Path.of(backupDir);
    }

    @Override
    public HealthCheckResponse call() {
        if (!Files.isDirectory(backupDir)) {
            return HealthCheckResponse.named(CHECK_NAME)
                .down()
                .withData("path", backupDir.toString())
                .withData("reason", "directory-missing")
                .build();
        }

        if (!Files.isWritable(backupDir)) {
            return HealthCheckResponse.named(CHECK_NAME)
                .down()
                .withData("path", backupDir.toString())
                .withData("reason", "directory-not-writable")
                .build();
        }

        return HealthCheckResponse.named(CHECK_NAME)
            .up()
            .withData("path", backupDir.toString())
            .build();
    }
}
