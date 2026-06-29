package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.MembershipStatus;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AuthServiceImpl implements AuthService {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final ParentAccountRepository parentAccountRepository;
    private final FamilyParentMembershipRepository membershipRepository;
    private final AppConfig appConfig;
    private final PasswordHasher passwordHasher;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TimeProvider timeProvider;
    private final GoogleIdentityVerifier googleIdentityVerifier;

    @Override
    public OperationResult<AuthPayload> authenticateAdmin(String email, String password) {
        log.debug("authenticateAdmin attempt for email={}", email);

        var parentOpt = parentAccountRepository.findByEmail(email);
        if (parentOpt.isEmpty()) {
            log.info("Authentication failed (parent account not found): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        var parent = parentOpt.get();
        if (parent.isBlocked()) {
            log.info("Authentication failed (account blocked): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }
        if (appConfig.emailVerification().enabled() && !parent.isVerified()) {
            log.info("Authentication failed (email not verified): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.emailNotVerified"));
        }

        String storedPassword = parent.getPasswordHash();
        if (!isPasswordValid(email, password, storedPassword)) {
            log.info("Authentication failed (wrong password): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.invalidPassword"));
        }

        return resolveMembershipAndAuthenticate(email, parent);
    }

    private OperationResult<AuthPayload> resolveMembershipAndAuthenticate(String email, ParentAccountEntity parent) {
        var memberships = membershipRepository.findByParentAccountId(parent.getId());
        if (memberships.isEmpty()) {
            log.info("Authentication failed (no active memberships): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.noActiveMemberships"));
        }

        List<AuthPayload.FamilyChoice> choices = buildFamilyChoices(memberships);

        if (memberships.size() == 1) {
            return authenticateWithMembership(email, memberships.get(0));
        }

        log.info("Multiple memberships found for email={}, returning family chooser", email);
        return OperationResult.success(
            new AuthPayload(null, email, "admin", null, null, false, null, choices, true));
    }

    private List<AuthPayload.FamilyChoice> buildFamilyChoices(List<FamilyParentMembershipEntity> memberships) {
        var choices = new ArrayList<AuthPayload.FamilyChoice>(memberships.size());
        for (var membership : memberships) {
            var familyOpt = familyRepository.findByDbId(membership.getFamilyId());
            if (familyOpt.isEmpty()) {
                continue;
            }

            FamilyEntity family = familyOpt.get();
            String familyName = family.getFamilyId();
            choices.add(new AuthPayload.FamilyChoice(
                family.getFamilyId(),
                familyName,
                membership.getPermission().name(),
                family.isBlocked()));
        }
        return choices;
    }

    private OperationResult<AuthPayload> authenticateWithMembership(String email,
                                                                    FamilyParentMembershipEntity membership) {
        var familyOpt = familyRepository.findByDbId(membership.getFamilyId());
        if (familyOpt.isEmpty()) {
            log.info("Authentication failed (family not found for membership): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            log.info("Authentication failed (family blocked): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.familyBlocked"));
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = isSuperAdminEmail(email);
        String permission = membership.getPermission().name();
        log.info("Admin login success: familyId={}, email={}, permission={}, isSuperAdmin={}",
            family.getFamilyId(), email, permission, isSuperAdmin);
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), email, "admin", null, null, isSuperAdmin, permission, null, false));
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

        var parentOpt = parentAccountRepository.findByEmail(identity.email());
        if (parentOpt.isEmpty()) {
            log.info("Google login failed: parent account not found for email={}", identity.email());
            return OperationResult.failure(BackendMessages.message("auth.googleAccountNotLinked"));
        }

        var parent = parentOpt.get();
        if (parent.isBlocked()) {
            log.info("Google login failed: account blocked for email={}", identity.email());
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        return resolveMembershipAndAuthenticate(identity.email(), parent);
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
        if (!isPasswordValid(email, password, storedPassword)) {
            log.info("Authentication failed (wrong password): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.invalidPassword"));
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = isSuperAdminEmail(email);
        log.info("Admin login success: familyId={}, email={}, isSuperAdmin={}",
            family.getFamilyId(), family.getEmail(), isSuperAdmin);
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "admin",
                null, null, isSuperAdmin, "family_admin", null, false));
    }

    private OperationResult<AuthPayload> authenticateGoogleFamily(String email, FamilyEntity family) {
        if (family.isBlocked()) {
            log.info("Google authentication failed (account blocked): {}", email);
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        if (appConfig.emailVerification().enabled() && !family.isVerified()) {
            boolean verified = familyRepository.verifyFamily(family.getFamilyId());
            if (!verified) {
                log.warn(
                    "Google authentication verified email but local verification update failed for familyId={}",
                    family.getFamilyId()
                );
            }
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = isSuperAdminEmail(email);
        log.info("Google admin login success: familyId={}, email={}, isSuperAdmin={}",
            family.getFamilyId(), family.getEmail(), isSuperAdmin);
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "admin",
                null, null, isSuperAdmin, "family_admin", null, false));
    }

    private boolean isPasswordValid(String email, String password, String storedPassword) {
        if (verifyArgon2Password(email, password, storedPassword)) {
            return true;
        }
        if (!passwordHasher.verifyLegacy(password, storedPassword)) {
            return false;
        }
        rehashLegacyPassword(email, password);
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

    private void rehashLegacyPassword(String email, String password) {
        try {
            String newHash = passwordHasher.hash(password);
            var parentOpt = parentAccountRepository.findByEmail(email);
            if (parentOpt.isPresent()) {
                parentOpt.get().setPasswordHash(newHash);
                log.info("Re-hashed legacy password for email={}", email);
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
                child.getId(), child.getName(), false, "child", null, false));
    }

    @Override
    @Transactional
    public OperationResult<AuthPayload> registerFamily(String email, String adminPassword) {
        if (parentAccountRepository.findByEmail(email).isPresent()) {
            return OperationResult.failure(BackendMessages.message("auth.emailRegistered"));
        }
        if (!isValidPassword(adminPassword)) {
            return OperationResult.failure(BackendMessages.message("auth.weakParentPassword"));
        }

        var familyId = email.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
        var verificationToken = appConfig.emailVerification().enabled() ? generateHexToken(32) : null;

        String hashedPassword = passwordHasher.hash(adminPassword);

        try {
            var parent = ParentAccountEntity.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .verified(!appConfig.emailVerification().enabled())
                .verificationToken(verificationToken)
                .build();
            parentAccountRepository.persistAndFlush(parent);

            var family = FamilyEntity.builder()
                .familyId(familyId)
                .email(email)
                .adminPassword(hashedPassword)
                .verified(!appConfig.emailVerification().enabled())
                .verificationToken(verificationToken)
                .build();
            familyRepository.persistAndFlush(family);

            var membership = FamilyParentMembershipEntity.builder()
                .parentAccountId(parent.getId())
                .familyId(family.getId())
                .permission(FamilyParentMembershipEntity.Permission.family_admin)
                .status(MembershipStatus.active)
                .build();
            membershipRepository.persistAndFlush(membership);

            log.info("New family registered: familyId={}, email={}", familyId, email);
            return OperationResult.success(
                new AuthPayload(familyId, email, "admin", null, null, false, "family_admin", null, false));
        } catch (Exception ex) {
            log.error("Failed to register family for email={}: {}", email, ex.getMessage());
            return OperationResult.failure(BackendMessages.message("auth.registrationFailed"));
        }
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

    @Override
    public OperationResult<AuthPayload> selectFamily(String email, String familyId) {
        var parentOpt = parentAccountRepository.findByEmail(email);
        if (parentOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        var parent = parentOpt.get();
        if (parent.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }
        var familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.familyBlocked"));
        }
        var membershipOpt = membershipRepository.findByParentAndFamily(parent.getId(), family.getId());
        if (membershipOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.noActiveMemberships"));
        }

        var membership = membershipOpt.get();
        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = isSuperAdminEmail(email);
        String permission = membership.getPermission().name();
        log.info("Family selected: familyId={}, email={}, permission={}", familyId, email, permission);
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), email, "admin", null, null, isSuperAdmin, permission, null, false));
    }
}
