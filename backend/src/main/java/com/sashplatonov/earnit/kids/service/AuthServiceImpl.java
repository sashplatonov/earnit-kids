package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AuthServiceImpl implements AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final AppConfig appConfig;
    private final PasswordHasher passwordHasher;

    @Override
    public OperationResult<AuthPayload> authenticateAdmin(String email, String pin) {
        log.debug("authenticateAdmin attempt for email={}", email);

        var superAdminResult = authenticateConfiguredSuperAdmin(email, pin);
        if (superAdminResult != null) {
            return superAdminResult;
        }

        var familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isEmpty()) {
            log.info("Authentication failed (family not found): {}", email);
            return OperationResult.failure("Неверные учетные данные");
        }

        return authenticateFamily(email, pin, familyOpt.get());
    }

    private OperationResult<AuthPayload> authenticateConfiguredSuperAdmin(String email, String pin) {
        var configuredSuperAdminEmail = appConfig.superAdmin().email().filter(value -> !value.isBlank());
        if (configuredSuperAdminEmail.isEmpty() || !configuredSuperAdminEmail.get().equals(email)) {
            return null;
        }
        if (appConfig.superAdmin().password().orElse("").equals(pin)) {
            log.info("Super-admin login success: {}", email);
            return OperationResult.success(new AuthPayload(null, email, "super_admin", null, null));
        }
        log.info("Super-admin login failed (wrong password): {}", email);
        return OperationResult.failure("Неверный пароль администратора");
    }

    private OperationResult<AuthPayload> authenticateFamily(String email, String pin, FamilyEntity family) {
        if (family.isBlocked()) {
            log.info("Authentication failed (account blocked): {}", email);
            return OperationResult.failure("Аккаунт заблокирован");
        }
        if (appConfig.emailVerification().enabled() && !family.isVerified()) {
            log.info("Authentication failed (email not verified): {}", email);
            return OperationResult.failure("Email не подтвержден. Проверьте почту.");
        }
        String storedPassword = family.getAdminPassword();
        if (!isPasswordValid(email, pin, family.getFamilyId(), storedPassword)) {
            log.info("Authentication failed (wrong password): {}", email);
            return OperationResult.failure("Неверный пароль");
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        log.info("Admin login success: familyId={}, email={}", family.getFamilyId(), family.getEmail());
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "admin", null, null));
    }

    private boolean isPasswordValid(String email, String pin, String familyId, String storedPassword) {
        if (verifyArgon2Password(email, pin, storedPassword)) {
            return true;
        }
        if (!passwordHasher.verifyLegacy(pin, storedPassword)) {
            return false;
        }
        rehashLegacyPassword(email, pin, familyId);
        return true;
    }

    private boolean verifyArgon2Password(String email, String pin, String storedPassword) {
        try {
            return passwordHasher.isArgon2Hash(storedPassword) && passwordHasher.verify(storedPassword, pin);
        } catch (Exception ex) {
            log.debug("Argon2 verify error for email={}: {}", email, ex.getMessage());
            return false;
        }
    }

    private void rehashLegacyPassword(String email, String pin, String familyId) {
        try {
            String newHash = passwordHasher.hash(pin);
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
            return OperationResult.failure("Токен отсутствует");
        }

        var childOpt = childRepository.findByToken(childToken);
        if (childOpt.isEmpty()) {
            log.info("Child auth failed: token not found");
            return OperationResult.failure("Неверная ссылка");
        }

        var child = childOpt.get();
        var familyOpt = familyRepository.findByDbId(child.getFamilyDbId());
        if (familyOpt.isEmpty()) {
            log.info("Child auth failed: family not found for childId={}", child.getId());
            return OperationResult.failure("Семья не найдена");
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            log.info("Child auth failed: account blocked for familyId={}", family.getFamilyId());
            return OperationResult.failure("Аккаунт заблокирован");
        }

        log.info("Child login success: familyId={}, childId={}", family.getFamilyId(), child.getId());
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "child",
                child.getId(), child.getName()));
    }

    @Override
    public OperationResult<AuthPayload> registerFamily(String email, String adminPin) {
        if (familyRepository.findByEmail(email).isPresent()) {
            return OperationResult.failure("Email уже зарегистрирован");
        }
        if (!isValidPassword(adminPin)) {
            return OperationResult.failure("Слабый пароль родителя");
        }

        var familyId = email.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
        var verificationToken = appConfig.emailVerification().enabled() ? generateHexToken(32) : null;

        String hashedPin = passwordHasher.hash(adminPin);
        var created = familyRepository.create(
            familyId, email, hashedPin, !appConfig.emailVerification().enabled(), verificationToken);

        if (created.isEmpty()) {
            return OperationResult.failure("Email уже зарегистрирован");
        }

        return OperationResult.success(
            new AuthPayload(familyId, email, "admin", null, null));
    }

    @Override
    public OperationResult<Void> forgotPassword(String email) {
        if (!appConfig.passwordRecovery().enabled()) {
            return OperationResult.failure("Функция восстановления пароля отключена");
        }

        var familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isPresent()) {
            var token = generateHexToken(32);
            var expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
            familyRepository.setResetToken(familyOpt.get().getFamilyId(), token, expiresAt);
            log.debug("Generated password reset token for a matching family account");
        }

        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> changeAdminPin(String familyId, String oldPin, String newPin) {
        if (familyId == null || familyId.isBlank()) {
            return OperationResult.failure("Семья не найдена");
        }
        if (!isValidPassword(newPin)) {
            return OperationResult.failure("Слабый пароль");
        }

        var familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        var family = familyOpt.get();
        String stored = family.getAdminPassword();
        boolean oldMatches = false;
        try {
            if (passwordHasher.isArgon2Hash(stored) && passwordHasher.verify(stored, oldPin)) {
                oldMatches = true;
            }
        } catch (Exception ignored) {
        }

        if (!oldMatches && passwordHasher.verifyLegacy(oldPin, stored)) {
            oldMatches = true;
        }

        if (!oldMatches) {
            return OperationResult.failure("Неверный пароль");
        }
        if (oldPin != null && oldPin.equals(newPin)) {
            return OperationResult.failure("Новый пароль должен отличаться от старого");
        }

        String newHash = passwordHasher.hash(newPin);
        boolean updated = familyRepository.updatePassword(familyId, newHash);
        if (!updated) {
            return OperationResult.failure("Не удалось обновить пароль");
        }

        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> resetPassword(String email, String token, String newPassword) {
        if (!isValidPassword(newPassword)) {
            return OperationResult.failure("Слабый пароль");
        }

        var familyOpt = familyRepository.findByResetToken(token);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Недействительная или просроченная ссылка");
        }

        var family = familyOpt.get();
        if (!family.getEmail().equalsIgnoreCase(email)) {
            return OperationResult.failure("Недействительная или просроченная ссылка");
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
            return OperationResult.failure("Недействительный токен верификации");
        }

        var family = familyOpt.get();
        if (!family.getEmail().equalsIgnoreCase(email)) {
            return OperationResult.failure("Недействительный токен верификации");
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
        var bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
