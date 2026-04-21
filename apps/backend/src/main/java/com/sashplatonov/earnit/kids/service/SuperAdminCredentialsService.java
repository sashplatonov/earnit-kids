package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.domain.model.SuperAdminCredentialEntity;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.SuperAdminCredentialRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuperAdminCredentialsService {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AppConfig appConfig;
    private final PasswordHasher passwordHasher;
    private final SuperAdminCredentialRepository superAdminCredentialRepository;

    public boolean matchesEmail(String email) {
        return configuredEmail().map(email::equals).orElse(false);
    }

    public boolean verifyPassword(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }

        Optional<SuperAdminCredentialEntity> override = passwordOverride();
        if (override.isPresent()) {
            return verifySecret(password, override.get().getPasswordHash());
        }

        return configuredPassword().map(secret -> verifySecret(password, secret)).orElse(false);
    }

    public OperationResult<Void> changePassword(String oldPassword, String newPassword) {
        if (configuredEmail().isEmpty()) {
            return OperationResult.failure("SUPER_ADMIN_NOT_CONFIGURED", BackendMessages.message("super.notConfigured"));
        }
        if (!isValidPassword(newPassword)) {
            return OperationResult.failure("WEAK_PASSWORD", BackendMessages.message("auth.weakPassword"));
        }
        if (!verifyPassword(oldPassword)) {
            return OperationResult.failure("INVALID_CURRENT_PASSWORD", BackendMessages.message("super.invalidCurrentPassword"));
        }
        if (oldPassword != null && oldPassword.equals(newPassword)) {
            return OperationResult.failure("PASSWORD_REUSE", BackendMessages.message("super.newPasswordMustDifferOld"));
        }

        superAdminCredentialRepository.upsertPasswordHash(configuredEmail().orElseThrow(), passwordHasher.hash(newPassword));
        return OperationResult.success(null);
    }

    private Optional<String> configuredEmail() {
        return appConfig.superAdmin().email().filter(value -> !value.isBlank());
    }

    private Optional<String> configuredPassword() {
        return appConfig.superAdmin().password().filter(value -> !value.isBlank());
    }

    private Optional<SuperAdminCredentialEntity> passwordOverride() {
        return configuredEmail().flatMap(email -> {
            Optional<SuperAdminCredentialEntity> credential = superAdminCredentialRepository.findByEmail(email);
            return credential == null ? Optional.empty() : credential;
        });
    }

    private boolean verifySecret(String suppliedPassword, String storedSecret) {
        if (storedSecret == null || storedSecret.isBlank()) {
            return false;
        }
        if (storedSecret.equals(suppliedPassword)) {
            return true;
        }

        try {
            if (passwordHasher.isArgon2Hash(storedSecret) && passwordHasher.verify(storedSecret, suppliedPassword)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return passwordHasher.verifyLegacy(suppliedPassword, storedSecret);
    }

    private boolean isValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        char first = password.charAt(0);
        return !password.chars().allMatch(c -> c == first);
    }
}