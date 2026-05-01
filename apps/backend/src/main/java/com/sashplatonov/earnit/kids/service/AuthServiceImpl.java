package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AuthServiceImpl implements AuthService {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final AppConfig appConfig;
    private final PasswordHasher passwordHasher;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TimeProvider timeProvider;
    private final GoogleIdentityVerifier googleIdentityVerifier;

    @Override
    public OperationResult<AuthPayload> authenticateAdmin(String email, String password) {
        log.debug("authenticateAdmin attempt for email={}", email);

        var familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isEmpty()) {
            log.info("Authentication failed (family not found): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        return authenticateFamily(email, password, familyOpt.get());
    }

    @Override
    public OperationResult<AuthPayload> authenticateAdminWithGoogle(String credential) {
        Optional<String> googleClientId = googleClientId();
        if (googleClientId.isEmpty()) {
            log.warn("Google login requested but Google auth is not configured");
            return OperationResult.failure(BackendMessages.message("auth.googleNotConfigured"));
        }

        var identityOpt = googleIdentityVerifier.verify(credential, googleClientId.get());
        if (identityOpt.isEmpty()) {
            log.info("Google login failed: token verification failed");
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        GoogleIdentity identity = identityOpt.get();
        if (!identity.emailVerified()) {
            log.info("Google login failed: email is not verified by provider");
            return OperationResult.failure(BackendMessages.message("auth.googleEmailNotVerified"));
        }

        var familyOpt = familyRepository.findByEmail(identity.email());
        if (familyOpt.isEmpty()) {
            log.info("Google login failed: family not found for email={}", identity.email());
            return OperationResult.failure(BackendMessages.message("auth.googleAccountNotLinked"));
        }

        return authenticateGoogleFamily(identity.email(), familyOpt.get());
    }

    private boolean isSuperAdminEmail(String email) {
        return appConfig.superAdmin().email()
            .filter(value -> !value.isBlank())
            .map(configuredEmail -> configuredEmail.equals(email))
            .orElse(false);
    }

    private OperationResult<AuthPayload> authenticateFamily(String email, String password, FamilyEntity family) {
        if (family.isBlocked()) {
            log.info("Authentication failed (account blocked): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }
        if (appConfig.emailVerification().enabled() && !family.isVerified()) {
            log.info("Authentication failed (email not verified): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.emailNotVerified"));
        }
        String storedPassword = family.getAdminPassword();
        if (!isPasswordValid(email, password, family.getFamilyId(), storedPassword)) {
            log.info("Authentication failed (wrong password): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.invalidPassword"));
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = isSuperAdminEmail(email);
        log.info("Admin login success: familyId={}, email={}, isSuperAdmin={}", family.getFamilyId(), family.getEmail(), isSuperAdmin);
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "admin", null, null, isSuperAdmin));
    }

    private OperationResult<AuthPayload> authenticateGoogleFamily(String email, FamilyEntity family) {
        if (family.isBlocked()) {
            log.info("Google authentication failed (account blocked): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        if (appConfig.emailVerification().enabled() && !family.isVerified()) {
            boolean verified = familyRepository.verifyFamily(family.getFamilyId());
            if (!verified) {
                log.warn("Google authentication verified email but local verification update failed for familyId={}", family.getFamilyId());
            }
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = isSuperAdminEmail(email);
        log.info("Google admin login success: familyId={}, email={}, isSuperAdmin={}", family.getFamilyId(), family.getEmail(), isSuperAdmin);
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "admin", null, null, isSuperAdmin));
    }

    private boolean isPasswordValid(String email, String password, String familyId, String storedPassword) {
        if (verifyArgon2Password(email, password, storedPassword)) {
            return true;
        }
        if (!passwordHasher.verifyLegacy(password, storedPassword)) {
            return false;
        }
        rehashLegacyPassword(email, password, familyId);
        return true;
    }

    private boolean verifyArgon2Password(String email, String password, String storedPassword) {
        try {
            return passwordHasher.isArgon2Hash(storedPassword) && passwordHasher.verify(storedPassword, password);
        } catch (Exception ex) {
            log.debug("Argon2 verify error for email={}: {}", email, ex.getMessage());
            return false;
        }
    }

    private void rehashLegacyPassword(String email, String password, String familyId) {
        try {
            String newHash = passwordHasher.hash(password);
            boolean updated = familyRepository.updatePassword(familyId, newHash);
            if (updated) {
                log.info("Re-hashed legacy password for familyId={}", familyId);
            } else {
                log.warn("Failed to persist re-hashed password for familyId={}", familyId);
            }
        } catch (Exception ex) {
            log.warn("Failed to re-hash legacy password for {}: {}", email, ex.getMessage());
        }
    }

    @Override
    public OperationResult<AuthPayload> authenticateChild(String childToken) {
        log.debug("authenticateChild attempt for token present={} (mask)", childToken != null && !childToken.isBlank());

        if (childToken == null || childToken.isBlank()) {
            log.info("Child auth failed: token missing");
            return OperationResult.failure(BackendMessages.message("auth.tokenMissing"));
        }

        var childOpt = childRepository.findByToken(childToken);
        if (childOpt.isEmpty()) {
            log.info("Child auth failed: token not found");
            return OperationResult.failure(BackendMessages.message("auth.invalidLink"));
        }

        var child = childOpt.get();
        var familyOpt = familyRepository.findByDbId(child.getFamilyDbId());
        if (familyOpt.isEmpty()) {
            log.info("Child auth failed: family not found for childId={}", child.getId());
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            log.info("Child auth failed: account blocked for familyId={}", family.getFamilyId());
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        log.info("Child login success: familyId={}, childId={}", family.getFamilyId(), child.getId());
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "child",
                child.getId(), child.getName(), false));
    }

    @Override
    public OperationResult<AuthPayload> registerFamily(String email, String adminPassword) {
        if (familyRepository.findByEmail(email).isPresent()) {
            return OperationResult.failure(BackendMessages.message("auth.emailRegistered"));
        }
        if (!isValidPassword(adminPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.weakParentPassword"));
        }

        var familyId = email.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
        var verificationToken = appConfig.emailVerification().enabled() ? generateHexToken(32) : null;

        String hashedPassword = passwordHasher.hash(adminPassword);
        var created = familyRepository.create(
            familyId, email, hashedPassword, !appConfig.emailVerification().enabled(), verificationToken);

        if (created.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.emailRegistered"));
        }

        return OperationResult.success(
            new AuthPayload(familyId, email, "admin", null, null, false));
    }

    @Override
    public OperationResult<Void> forgotPassword(String email) {
        if (!appConfig.passwordRecovery().enabled()) {
            return OperationResult.failure(BackendMessages.message("auth.passwordRecoveryDisabled"));
        }

        var familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isPresent()) {
            var token = generateHexToken(32);
            var expiresAt = timeProvider.now().plus(1, ChronoUnit.HOURS);
            familyRepository.setResetToken(familyOpt.get().getFamilyId(), token, expiresAt);
            log.debug("Generated password reset token for a matching family account");
        }

        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> changeAdminPassword(String familyId, String oldPassword, String newPassword) {
        if (familyId == null || familyId.isBlank()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }
        if (!isValidPassword(newPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.weakPassword"));
        }

        var familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        String stored = family.getAdminPassword();
        boolean oldMatches = false;
        try {
            if (passwordHasher.isArgon2Hash(stored) && passwordHasher.verify(stored, oldPassword)) {
                oldMatches = true;
            }
        } catch (Exception ignored) {
        }

        if (!oldMatches && passwordHasher.verifyLegacy(oldPassword, stored)) {
            oldMatches = true;
        }

        if (!oldMatches) {
            return OperationResult.failure(BackendMessages.message("auth.invalidPassword"));
        }
        if (oldPassword != null && oldPassword.equals(newPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.newPasswordMustDiffer"));
        }

        String newHash = passwordHasher.hash(newPassword);
        boolean updated = familyRepository.updatePassword(familyId, newHash);
        if (!updated) {
            return OperationResult.failure(BackendMessages.message("auth.passwordUpdateFailed"));
        }

        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> resetPassword(String email, String token, String newPassword) {
        if (!isValidPassword(newPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.weakPassword"));
        }

        var familyOpt = familyRepository.findByResetToken(token);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidOrExpiredResetLink"));
        }

        var family = familyOpt.get();
        if (!family.getEmail().equalsIgnoreCase(email)) {
            return OperationResult.failure(BackendMessages.message("auth.invalidOrExpiredResetLink"));
        }

        String newHash = passwordHasher.hash(newPassword);
        familyRepository.updatePassword(family.getFamilyId(), newHash);
        familyRepository.clearResetToken(family.getFamilyId());
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> verifyEmail(String email, String token) {
        var familyOpt = familyRepository.findByVerificationToken(token);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidVerificationToken"));
        }

        var family = familyOpt.get();
        if (!family.getEmail().equalsIgnoreCase(email)) {
            return OperationResult.failure(BackendMessages.message("auth.invalidVerificationToken"));
        }

        familyRepository.verifyFamily(family.getFamilyId());
        return OperationResult.success(null);
    }

    private boolean isValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        char first = password.charAt(0);
        return !password.chars().allMatch(c -> c == first);
    }

    private String generateHexToken(int byteCount) {
        return secureTokenGenerator.generateHexToken(byteCount);
    }

    private Optional<String> googleClientId() {
        if (!appConfig.google().enabled()) {
            return Optional.empty();
        }

        return appConfig.google().clientId()
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    }
}



