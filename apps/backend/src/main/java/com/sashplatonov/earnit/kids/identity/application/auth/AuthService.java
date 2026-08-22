package com.sashplatonov.earnit.kids.identity.application.auth;

import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface AuthService {

    OperationResult<AuthPayload> authenticateAdmin(String email, String password);

    OperationResult<AuthPayload> authenticateAdminWithGoogle(String credential);

    OperationResult<AuthPayload> authenticateChild(String childToken);

    OperationResult<AuthPayload> registerFamily(String email, String adminPassword);

    OperationResult<Void> changeAdminPassword(String familyId, String oldPassword, String newPassword);

    OperationResult<AuthPayload> selectFamily(String email, String familyId);
}
