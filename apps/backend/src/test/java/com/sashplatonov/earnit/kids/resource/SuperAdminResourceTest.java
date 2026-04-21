package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.ToggleFamilyBlockRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateBackupTelegramSettingsRequest;
import com.sashplatonov.earnit.kids.dto.response.BackupTelegramSettingsResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import com.sashplatonov.earnit.kids.service.BackupTelegramSettingsService;
import com.sashplatonov.earnit.kids.service.DatabaseBackupService;
import com.sashplatonov.earnit.kids.service.SuperAdminService;
import com.sashplatonov.earnit.kids.service.SystemDashboardService;
import com.sashplatonov.earnit.kids.service.TelegramBackupService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminResourceTest {

    @Mock SuperAdminService superAdminService;
    @Mock SystemDashboardService systemDashboardService;
    @Mock DatabaseBackupService databaseBackupService;
    @Mock BackupTelegramSettingsService backupTelegramSettingsService;
    @Mock TelegramBackupService telegramBackupService;

    private SuperAdminResource resource;

    @BeforeEach
    void setUp() {
        RequestLocaleHolder.set("en");
        resource = new SuperAdminResource(
            superAdminService,
            systemDashboardService,
            databaseBackupService,
            backupTelegramSettingsService,
            telegramBackupService
        );
    }

    @AfterEach
    void tearDown() {
        RequestLocaleHolder.clear();
    }

    @Test
    void getFamilies_requiresSuperAdminRole() {
        assertThat(resource.getFamilies(contextWithAuth(null)).getStatus()).isEqualTo(401);
        assertThat(resource.getFamilies(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFamilies_superAdmin_returnsPayload() {
        when(superAdminService.getFamilies()).thenReturn(List.of(Map.of("id", "fam-1")));

        Response response = resource.getFamilies(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((Map<String, Object>) response.getEntity())
            .containsEntry("families", List.of(Map.of("id", "fam-1")));
    }

    @Test
    void getFamilyDetails_notFound_returns404() {
        when(superAdminService.getFamilyDetails("missing")).thenReturn(null);

        Response response = resource.getFamilyDetails(contextWithAuth(superAdminAuth()), "missing");

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void getFamilyDetails_superAdmin_returnsData() {
        when(superAdminService.getFamilyDetails("fam-1")).thenReturn(Map.of("id", "fam-1", "email", "a@test.com"));

        Response response = resource.getFamilyDetails(contextWithAuth(superAdminAuth()), "fam-1");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void toggleFamilyBlock_superAdmin_delegatesToService() {
        when(superAdminService.setFamilyBlocked("fam-1", true)).thenReturn(true);

        Response response = resource.toggleFamilyBlock(
            contextWithAuth(superAdminAuth()),
            "fam-1",
            new ToggleFamilyBlockRequest(true)
        );

        assertThat(response.getStatus()).isEqualTo(200);
        verify(superAdminService).setFamilyBlocked("fam-1", true);
    }

    @Test
    void toggleFamilyBlock_familyNotFound_returns404() {
        when(superAdminService.setFamilyBlocked("missing", false)).thenReturn(false);

        Response response = resource.toggleFamilyBlock(
            contextWithAuth(superAdminAuth()),
            "missing",
            new ToggleFamilyBlockRequest(false)
        );

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void toggleFamilyBlock_nullRequest_returns400() {
        Response response = resource.toggleFamilyBlock(contextWithAuth(superAdminAuth()), "fam-1", null);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void regenerateFamilyToken_superAdmin_returnsToken() {
        when(superAdminService.regenerateFamilyToken("fam-1")).thenReturn(OperationResult.success("new-token"));

        Response response = resource.regenerateFamilyToken(contextWithAuth(superAdminAuth()), "fam-1");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(Map.of("success", true, "token", "new-token"));
    }

    @Test
    void regenerateFamilyToken_failure_returns400() {
        when(superAdminService.regenerateFamilyToken("fam-1")).thenReturn(OperationResult.failure("not found"));

        Response response = resource.regenerateFamilyToken(contextWithAuth(superAdminAuth()), "fam-1");

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void regenerateChildToken_superAdmin_returnsTokenPayload() {
        when(superAdminService.regenerateChildToken(10)).thenReturn(OperationResult.success("token-10"));

        Response response = resource.regenerateChildToken(contextWithAuth(superAdminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(Map.of("success", true, "token", "token-10"));
    }

    @Test
    void setFamilyPassword_superAdmin_returnsOk() {
        when(superAdminService.setFamilyPassword("fam-1", "newpass123")).thenReturn(OperationResult.success(null));

        Response response = resource.setFamilyPassword(
            contextWithAuth(superAdminAuth()),
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.SetPasswordRequest("newpass123")
        );

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void setFamilyPassword_missingFamily_returns404() {
        when(superAdminService.setFamilyPassword("missing", "newpass123"))
            .thenReturn(OperationResult.failure("FAMILY_NOT_FOUND", BackendMessages.message("family.familyNotFound")));

        Response response = resource.setFamilyPassword(
            contextWithAuth(superAdminAuth()),
            "missing",
            new com.sashplatonov.earnit.kids.dto.request.SetPasswordRequest("newpass123")
        );

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void changeSuperAdminPassword_success_returnsOk() {
        when(superAdminService.changeSuperAdminPassword("admin123", "newpass123"))
            .thenReturn(OperationResult.success(null));

        Response response = resource.changeSuperAdminPassword(
            contextWithAuth(superAdminAuth()),
            new com.sashplatonov.earnit.kids.dto.request.ChangePasswordRequest("admin123", "newpass123")
        );

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void changeSuperAdminPassword_failure_returns400() {
        when(superAdminService.changeSuperAdminPassword("wrong", "newpass123"))
            .thenReturn(OperationResult.failure("INVALID_CURRENT_PASSWORD", BackendMessages.message("super.invalidCurrentPassword")));

        Response response = resource.changeSuperAdminPassword(
            contextWithAuth(superAdminAuth()),
            new com.sashplatonov.earnit.kids.dto.request.ChangePasswordRequest("wrong", "newpass123")
        );

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void getBaseData_superAdmin_returnsData() {
        when(superAdminService.getBaseData()).thenReturn(Map.of("tasks", List.of(), "products", List.of()));

        Response response = resource.getBaseData(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getBaseData_requiresSuperAdmin() {
        assertThat(resource.getBaseData(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    void saveBaseData_success_returnsOk() {
        when(superAdminService.saveBaseData(Map.of("tasks", List.of()))).thenReturn(true);

        Response response = resource.saveBaseData(contextWithAuth(superAdminAuth()), Map.of("tasks", List.of()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void saveBaseData_failure_returns500() {
        when(superAdminService.saveBaseData(Map.of())).thenReturn(false);

        Response response = resource.saveBaseData(contextWithAuth(superAdminAuth()), Map.of());

        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void getSystemOverview_superAdmin_returnsPayload() {
        when(systemDashboardService.getOverview()).thenReturn(Map.of("process", Map.of("uptimeSec", 100L)));

        Response response = resource.getSystemOverview(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getOverview();
    }

    @Test
    void getSystemOverview_requiresSuperAdmin() {
        assertThat(resource.getSystemOverview(contextWithAuth(null)).getStatus()).isEqualTo(401);
        assertThat(resource.getSystemOverview(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    void getDatabaseHealth_superAdmin_returnsPayload() {
        when(systemDashboardService.getDbHealth()).thenReturn(Map.of("db", Map.of("connected", true)));

        Response response = resource.getDatabaseHealth(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getDbHealth();
    }

    @Test
    void getHttpMetrics_superAdmin_returnsPayload() {
        when(systemDashboardService.getHttpMetrics()).thenReturn(Map.of("routes", List.of()));

        Response response = resource.getHttpMetrics(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getHttpMetrics();
    }

    @Test
    void getLogs_superAdmin_forwardslevelAndLimit() {
        when(systemDashboardService.getLogs("error", 50)).thenReturn(Map.of("logs", List.of()));

        Response response = resource.getLogs(contextWithAuth(superAdminAuth()), "error", 50);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getLogs("error", 50);
    }

    @Test
    void getLogs_nullParams_usesDefaults() {
        when(systemDashboardService.getLogs("all", 100)).thenReturn(Map.of("logs", List.of()));

        Response response = resource.getLogs(contextWithAuth(superAdminAuth()), null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getLogs("all", 100);
    }

    @Test
    void restoreDatabase_emptyPayload_returns500() {
        when(databaseBackupService.restoreBackup(new byte[0]))
            .thenReturn(OperationResult.failure(BackendMessages.message("backup.emptyFile")));

        Response response = resource.restoreDatabase(contextWithAuth(superAdminAuth()), new byte[0]);

        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void restoreDatabase_success_returnsOk() {
        byte[] payload = new byte[]{1, 2, 3};
        when(databaseBackupService.restoreBackup(payload)).thenReturn(OperationResult.success(null));

        Response response = resource.restoreDatabase(contextWithAuth(superAdminAuth()), payload);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getBackupTelegramSettings_returnsPayload() {
        BackupTelegramSettingsResponse payload = new BackupTelegramSettingsResponse(
            true,
            "chat-1",
            12,
            true,
            true,
            null,
            null,
            null
        );
        when(backupTelegramSettingsService.getSettings()).thenReturn(payload);

        Response response = resource.getBackupTelegramSettings(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void updateBackupTelegramSettings_invalid_returns400() {
        UpdateBackupTelegramSettingsRequest request =
            new UpdateBackupTelegramSettingsRequest(true, null, "chat-1", 24);
        when(backupTelegramSettingsService.updateSettings(request))
            .thenReturn(OperationResult.failure(BackendMessages.message("backup.botTokenRequired")));

        Response response = resource.updateBackupTelegramSettings(contextWithAuth(superAdminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void sendBackupToTelegram_notConfigured_returns503() throws Exception {
        when(telegramBackupService.isConfigured()).thenReturn(false);

        Response response = resource.sendBackupToTelegram(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void sendBackupToTelegram_success_returnsOk() throws Exception {
        DatabaseBackupService.BackupArtifact artifact =
            new DatabaseBackupService.BackupArtifact(Path.of("backup.dump"), "backup.dump");
        when(telegramBackupService.isConfigured()).thenReturn(true);
        when(databaseBackupService.createBackup()).thenReturn(OperationResult.success(artifact));
        when(telegramBackupService.sendBackup(artifact.path(), artifact.filename()))
            .thenReturn(OperationResult.success(null));

        Response response = resource.sendBackupToTelegram(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf");
    }

    private static AuthContext superAdminAuth() {
        return new AuthContext("fam-1", null, "super_admin", "root@test.com", "csrf");
    }
}