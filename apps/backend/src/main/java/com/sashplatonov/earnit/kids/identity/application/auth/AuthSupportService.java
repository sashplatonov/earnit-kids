package com.sashplatonov.earnit.kids.identity.application.auth;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.PasswordHasher;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
class AuthSupportService {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AppConfig appConfig;
    private final PasswordHasher passwordHasher;
    private final ParentAccountRepository parentAccountRepository;
    private final SecureTokenGenerator secureTokenGenerator;

    boolean isValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        char first = password.charAt(0);
        return !password.chars().allMatch(c -> c == first);
    }

    boolean isPasswordValid(String email, String password, String storedPassword) {
        if (verifyArgon2Password(email, password, storedPassword)) {
            return true;
        }
        if (!passwordHasher.verifyLegacy(password, storedPassword)) {
            return false;
        }
        rehashLegacyPassword(email, password);
        return true;
    }

    String hashPassword(String password) {
        return passwordHasher.hash(password);
    }

    String generateHexToken(int byteCount) {
        return secureTokenGenerator.generateHexToken(byteCount);
    }

    Optional<String> googleClientId() {
        if (!appConfig.google().enabled()) {
            return Optional.empty();
        }

        return appConfig.google().clientId()
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    }

    private boolean verifyArgon2Password(String email, String password, String storedPassword) {
        try {
            return passwordHasher.isArgon2Hash(storedPassword) && passwordHasher.verify(storedPassword, password);
        } catch (Exception ex) {
            return false;
        }
    }

    private void rehashLegacyPassword(String email, String password) {
        try {
            String newHash = passwordHasher.hash(password);
            var parentOpt = parentAccountRepository.findByEmail(email);
            if (parentOpt.isPresent()) {
                parentOpt.get().setPasswordHash(newHash);
            }
        } catch (Exception ignored) {
        }
    }
}
