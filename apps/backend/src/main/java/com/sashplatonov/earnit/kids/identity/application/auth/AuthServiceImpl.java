package com.sashplatonov.earnit.kids.identity.application.auth;

import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.sashplatonov.earnit.kids.platform.application.observability.BackendKpiMetrics;
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AuthServiceImpl implements AuthService {
    private final AuthAdminAuthService adminAuthService;
    private final AuthChildAuthService childAuthService;
    private final AuthLifecycleService lifecycleService;
    private final AuthMembershipService membershipService;
    private final BackendKpiMetrics backendKpiMetrics;

    @Override
    public OperationResult<AuthPayload> authenticateAdmin(String email, String password) {
        return backendKpiMetrics.recordResult("auth", "admin_password", () ->
            adminAuthService.authenticateAdmin(email, password));
    }

    @Override
    public OperationResult<AuthPayload> authenticateAdminWithGoogle(String credential) {
        return backendKpiMetrics.recordResult("auth", "admin_google", () ->
            adminAuthService.authenticateAdminWithGoogle(credential));
    }

    @Override
    public OperationResult<AuthPayload> authenticateChild(String childToken) {
        return backendKpiMetrics.recordResult("auth", "child", () -> childAuthService.authenticateChild(childToken));
    }

    @Override
    public OperationResult<AuthPayload> registerFamily(String email, String adminPassword) {
        return backendKpiMetrics.recordResult("auth", "register_family", () ->
            lifecycleService.registerFamily(email, adminPassword));
    }

    @Override
    public OperationResult<Void> changeAdminPassword(String familyId, String oldPassword, String newPassword) {
        return backendKpiMetrics.recordResult("auth", "change_admin_password", () ->
            lifecycleService.changeAdminPassword(familyId, oldPassword, newPassword));
    }

    @Override
    public OperationResult<AuthPayload> selectFamily(Integer parentAccountId, String familyId) {
        return backendKpiMetrics.recordResult("auth", "select_family", () ->
            membershipService.selectFamily(parentAccountId, familyId));
    }

    @Deprecated
    @Override
    public OperationResult<AuthPayload> selectFamily(String ignoredEmail, String familyId) {
        return selectFamily((Integer) null, familyId);
    }
}
