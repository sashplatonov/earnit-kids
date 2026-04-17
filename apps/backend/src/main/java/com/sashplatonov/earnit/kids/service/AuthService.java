package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface AuthService {

    OperationResult<AuthPayload> authenticateAdmin(String email, String password);

    OperationResult<AuthPayload> authenticateChild(String childToken);

    OperationResult<AuthPayload> registerFamily(String email, String adminPassword);

    OperationResult<Void> forgotPassword(String email);

    OperationResult<Void> changeAdminPassword(String familyId, String oldPassword, String newPassword);

    OperationResult<Void> resetPassword(String email, String token, String newPassword);

    OperationResult<Void> verifyEmail(String email, String token);
}
