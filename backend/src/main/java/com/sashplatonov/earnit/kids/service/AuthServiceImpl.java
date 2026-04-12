package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
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

    @Override
    public OperationResult<AuthPayload> authenticateAdmin(String email, String pin) {
        var configuredSuperAdminEmail = appConfig.superAdmin().email().filter(value -> !value.isBlank());
        if (configuredSuperAdminEmail.isPresent() && configuredSuperAdminEmail.get().equals(email)) {
            if (appConfig.superAdmin().password().orElse("").equals(pin)) {
                return OperationResult.success(
                    new AuthPayload(null, email, "super_admin", null, null));
            }
            return OperationResult.failure("Неверный пароль администратора");
        }

        var familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Неверные учетные данные");
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure("Аккаунт заблокирован");
        }
        if (appConfig.emailVerification().enabled() && !family.isVerified()) {
            return OperationResult.failure("Email не подтвержден. Проверьте почту.");
        }
        if (!family.getAdminPassword().equals(pin)) {
            return OperationResult.failure("Неверный пароль");
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "admin", null, null));
    }

    @Override
    public OperationResult<AuthPayload> authenticateChild(String childToken) {
        if (childToken == null || childToken.isBlank()) {
            return OperationResult.failure("Токен отсутствует");
        }

        var childOpt = childRepository.findByToken(childToken);
        if (childOpt.isEmpty()) {
            return OperationResult.failure("Неверная ссылка");
        }

        var child = childOpt.get();
        var familyOpt = familyRepository.findByDbId(child.getFamilyDbId());
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure("Аккаунт заблокирован");
        }

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

        var created = familyRepository.create(
            familyId, email, adminPin, !appConfig.emailVerification().enabled(), verificationToken);

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
        if (!family.getAdminPassword().equals(oldPin)) {
            return OperationResult.failure("Неверный пароль");
        }
        if (oldPin != null && oldPin.equals(newPin)) {
            return OperationResult.failure("Новый пароль должен отличаться от старого");
        }

        boolean updated = familyRepository.updatePassword(familyId, newPin);
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

        familyRepository.updatePassword(family.getFamilyId(), newPassword);
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
