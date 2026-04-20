package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.domain.model.SuperAdminCredentialEntity;
import com.sashplatonov.earnit.kids.repository.SuperAdminCredentialRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminCredentialsServiceTest {

    @Mock SuperAdminCredentialRepository superAdminCredentialRepository;

    private PasswordHasher passwordHasher;
    private SuperAdminCredentialsService service;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
        service = new SuperAdminCredentialsService(
            TestConfigFactory.appConfig(false, "admin@test.com", "admin123", false, true),
            passwordHasher,
            superAdminCredentialRepository
        );
    }

    @Test
    void matchesEmail_usesConfiguredSuperAdminEmail() {
        assertThat(service.matchesEmail("admin@test.com")).isTrue();
        assertThat(service.matchesEmail("other@test.com")).isFalse();
    }

    @Test
    void verifyPassword_withoutOverride_usesConfiguredPassword() {
        assertThat(service.verifyPassword("admin123")).isTrue();
        assertThat(service.verifyPassword("wrong")).isFalse();
    }

    @Test
    void verifyPassword_withOverride_usesStoredHash() {
        when(superAdminCredentialRepository.findByEmail("admin@test.com"))
            .thenReturn(Optional.of(SuperAdminCredentialEntity.builder()
                .email("admin@test.com")
                .passwordHash(passwordHasher.hash("overridePass9"))
                .build()));

        assertThat(service.verifyPassword("overridePass9")).isTrue();
        assertThat(service.verifyPassword("admin123")).isFalse();
    }

    @Test
    void changePassword_matchingConfiguredPassword_persistsOverrideHash() {
        when(superAdminCredentialRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        OperationResult<Void> result = service.changePassword("admin123", "newpass123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(superAdminCredentialRepository).upsertPasswordHash(
            eq("admin@test.com"),
            argThat(hash -> passwordHasher.isArgon2Hash(hash) && passwordHasher.verify(hash, "newpass123"))
        );
    }

    @Test
    void changePassword_wrongCurrentPassword_returnsFailure() {
        assertThat(service.changePassword("wrong", "newpass123"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changePassword_weakPassword_returnsFailure() {
        assertThat(service.changePassword("admin123", "111111"))
            .isInstanceOf(OperationResult.Failure.class);
    }
}