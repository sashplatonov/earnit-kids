package com.sashplatonov.earnit.kids.service.auth;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.sashplatonov.earnit.kids.service.google.GoogleIdentity;
import com.sashplatonov.earnit.kids.service.google.GoogleIdentityVerifier;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
class AuthAdminAuthService {
    private final ParentAccountRepository parentAccountRepository;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final AuthSupportService supportService;
    private final AuthMembershipService membershipService;

    OperationResult<AuthPayload> authenticateAdmin(String email, String password) {
        var parentOpt = parentAccountRepository.findByEmail(email);
        if (parentOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        var parent = parentOpt.get();
        if (parent.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        String storedPassword = parent.getPasswordHash();
        if (!supportService.isPasswordValid(email, password, storedPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.invalidPassword"));
        }

        return membershipService.resolveMembershipAndAuthenticate(email, parent);
    }

    OperationResult<AuthPayload> authenticateAdminWithGoogle(String credential) {
        var googleClientId = supportService.googleClientId();
        if (googleClientId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.googleNotConfigured"));
        }

        var identityOpt = googleIdentityVerifier.verify(credential, googleClientId.get());
        if (identityOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        GoogleIdentity identity = identityOpt.get();
        if (!identity.emailVerified()) {
            return OperationResult.failure(BackendMessages.message("auth.googleEmailNotVerified"));
        }

        var parentOpt = parentAccountRepository.findByEmail(identity.email());
        if (parentOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.googleAccountNotLinked"));
        }

        var parent = parentOpt.get();
        if (parent.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        return membershipService.resolveMembershipAndAuthenticate(identity.email(), parent);
    }
}
