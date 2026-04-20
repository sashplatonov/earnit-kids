package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-20T10:15:30Z");

    @Mock DatabaseCommandRunner commandRunner;

    private DatabaseBackupService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseBackupService(
            "jdbc:postgresql://db:5432/earnit_kids",
            "earnit",
            Optional.of("secret"),
            "earnit_kids",
            TestConfigFactory.timeProvider(FIXED_NOW),
            commandRunner
        );
    }

    @Test
    void createBackup_limitsDumpToConfiguredSchema() throws Exception {
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenReturn(new DatabaseCommandResult(0, ""));

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
    void restoreBackup_resetsSchemaBeforeSchemaScopedRestore() throws Exception {
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenReturn(new DatabaseCommandResult(0, ""));

        OperationResult<Void> result = service.restoreBackup(new byte[]{1, 2, 3});

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner, times(2)).run(commandCaptor.capture(), org.mockito.ArgumentMatchers.eq("secret"));
        List<List<String>> commands = commandCaptor.getAllValues();
        assertThat(commands.get(0)).containsSequence(
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
        assertThat(commands.get(0)).contains("--command", "DROP SCHEMA IF EXISTS \"earnit_kids\" CASCADE; CREATE SCHEMA \"earnit_kids\";");
        assertThat(commands.get(1)).containsSequence(
            "pg_restore",
            "--exit-on-error",
            "--single-transaction",
            "--no-owner",
            "--no-privileges",
            "--schema",
            "earnit_kids"
        );
        assertThat(commands.get(1)).doesNotContain("--clean", "--if-exists");
        assertThat(commands.get(1)).contains("--host", "db", "--port", "5432", "--username", "earnit", "--dbname", "earnit_kids");
    }

    @Test
    void restoreBackup_schemaResetFailure_returnsFailure() throws Exception {
        when(commandRunner.run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret")))
            .thenReturn(new DatabaseCommandResult(1, "schema reset failed"));

        OperationResult<Void> result = service.restoreBackup(new byte[]{1, 2, 3});

        assertThat(result).isEqualTo(OperationResult.failure("schema reset failed"));
        verify(commandRunner, times(1)).run(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("secret"));
    }
}
