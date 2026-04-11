package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@ApplicationScoped
public final class AuthServiceImpl implements AuthService {
    private static final Logger LOG = Logger.getLogger(AuthServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final String superAdminEmail;
    private final String superAdminPassword;
    private final boolean emailVerificationEnabled;
    private final boolean passwordRecoveryEnabled;

    @Inject
    public AuthServiceImpl(FamilyRepository familyRepository,
                           ChildRepository childRepository,
                           @ConfigProperty(name = "app.super-admin.email", defaultValue = "")
                           String superAdminEmail,
                           @ConfigProperty(name = "app.super-admin.password", defaultValue = "")
                           String superAdminPassword,
                           @ConfigProperty(name = "app.email-verification.enabled", defaultValue = "true")
                           boolean emailVerificationEnabled,
                           @ConfigProperty(name = "app.password-recovery.enabled", defaultValue = "true")
                           boolean passwordRecoveryEnabled) {
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.superAdminEmail = superAdminEmail;
        this.superAdminPassword = superAdminPassword;
        this.emailVerificationEnabled = emailVerificationEnabled;
        this.passwordRecoveryEnabled = passwordRecoveryEnabled;
    }

    @Override
    public OperationResult<AuthPayload> authenticateAdmin(String email, String pin) {
        if (!superAdminEmail.isBlank() && superAdminEmail.equals(email)) {
            if (superAdminPassword.equals(pin)) {
                return OperationResult.success(
                    new AuthPayload(null, email, "super_admin", null, null));
            }
            return OperationResult.failure("Неверный пароль администратора");
        }

        Optional<FamilyEntity> familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Неверные учетные данные");
        }

        FamilyEntity family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure("Аккаунт заблокирован");
        }
        if (emailVerificationEnabled && !family.isVerified()) {
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

        FamilyEntity family = familyOpt.get();
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

        String familyId = email.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
        String verificationToken = emailVerificationEnabled ? generateHexToken(32) : null;

        Optional<FamilyEntity> created = familyRepository.create(
            familyId, email, adminPin, !emailVerificationEnabled, verificationToken);

        if (created.isEmpty()) {
            return OperationResult.failure("Email уже зарегистрирован");
        }

        return OperationResult.success(
            new AuthPayload(familyId, email, "admin", null, null));
    }

    @Override
    public OperationResult<Void> forgotPassword(String email) {
        if (!passwordRecoveryEnabled) {
            return OperationResult.failure("Функция восстановления пароля отключена");
        }

        Optional<FamilyEntity> familyOpt = familyRepository.findByEmail(email);
        if (familyOpt.isPresent()) {
            String token = generateHexToken(32);
            Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
            familyRepository.setResetToken(familyOpt.get().getFamilyId(), token, expiresAt);
            LOG.infof("Password reset token generated for %s", email);
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

        Optional<FamilyEntity> familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        FamilyEntity family = familyOpt.get();
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

        Optional<FamilyEntity> familyOpt = familyRepository.findByResetToken(token);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Недействительная или просроченная ссылка");
        }

        FamilyEntity family = familyOpt.get();
        if (!family.getEmail().equalsIgnoreCase(email)) {
            return OperationResult.failure("Недействительная или просроченная ссылка");
        }

        familyRepository.updatePassword(family.getFamilyId(), newPassword);
        familyRepository.clearResetToken(family.getFamilyId());
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> verifyEmail(String email, String token) {
        Optional<FamilyEntity> familyOpt = familyRepository.findByVerificationToken(token);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure("Недействительный токен верификации");
        }

        FamilyEntity family = familyOpt.get();
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
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(byteCount * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
