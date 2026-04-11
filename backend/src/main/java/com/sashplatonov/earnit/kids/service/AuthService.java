package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface AuthService {

    OperationResult<AuthPayload> authenticateAdmin(String email, String pin);

    OperationResult<AuthPayload> authenticateChild(String childToken);

    OperationResult<AuthPayload> registerFamily(String email, String adminPin);

    OperationResult<Void> forgotPassword(String email);

    OperationResult<Void> changeAdminPin(String familyId, String oldPin, String newPin);

    OperationResult<Void> resetPassword(String email, String token, String newPassword);

    OperationResult<Void> verifyEmail(String email, String token);
}
