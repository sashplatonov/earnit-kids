package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.PasswordHasher;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.SuperAdminCredentialRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");
    private static final SecureTokenGenerator TOKEN_GENERATOR = new SecureTokenGenerator();

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;
    @Mock SuperAdminCredentialRepository superAdminCredentialRepository;
    @Mock GoogleIdentityVerifier googleIdentityVerifier;

    AuthServiceImpl authService;
    PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        RequestLocaleHolder.set("en");
        passwordHasher = new PasswordHasher();
        authService = createAuthService(TestConfigFactory.appConfig(false, "admin@test.com", "admin123", false, true));
    }

    @Test
    void authenticateAdmin_matchingSuperAdminCredentials_returnsSuperAdminPayload() {
        OperationResult<AuthPayload> result = authService.authenticateAdmin("admin@test.com", "admin123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<AuthPayload> success1 = (OperationResult.Success<AuthPayload>) result;
        AuthPayload payload = success1.value();
        assertThat(payload).isNotNull();
        assertThat(payload.role()).isEqualTo("super_admin");
        assertThat(payload.email()).isEqualTo("admin@test.com");
    }

    @Test
    void authenticateAdmin_wrongSuperAdminPassword_returnsFailure() {
        OperationResult<AuthPayload> result = authService.authenticateAdmin("admin@test.com", "wrong");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure1 = (OperationResult.Failure<?>) result;
        assertThat(failure1.message())
            .contains("Invalid super-admin password");
    }

    @Test
    void authenticateAdmin_familyCredentialsMatch_returnsAdminPayload() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, true);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);
        when(familyRepository.updatePassword(eq("fam_1"), anyString())).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<AuthPayload> success2 = (OperationResult.Success<AuthPayload>) result;
        AuthPayload payload = success2.value();
        assertThat(payload).isNotNull();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("fam_1");
        verify(familyRepository).updatePassword(eq("fam_1"), argThat(hash -> hash.startsWith("$argon2")));
    }

    @Test
    void authenticateAdmin_argon2Password_returnsAdminPayloadWithoutRehash() {
        String hashedPassword = passwordHasher.hash("password123");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", hashedPassword, false, true);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository, never()).updatePassword(eq("fam_1"), anyString());
    }

    @Test
    void authenticateAdmin_sha256Password_returnsAdminPayloadAndRehashes() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", sha256Hex("password123"), false, true);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);
        when(familyRepository.updatePassword(eq("fam_1"), anyString())).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).updatePassword(eq("fam_1"), argThat(hash -> hash.startsWith("$argon2")));
    }

    @Test
    void authenticateAdmin_blockedFamily_returnsFailure() {
        FamilyEntity family = mockFamily("fam_1", "blocked@test.com", "password123", true, true);
        when(familyRepository.findByEmail("blocked@test.com")).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = authService.authenticateAdmin("blocked@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure2 = (OperationResult.Failure<?>) result;
        assertThat(failure2.message())
            .isEqualTo("Account is blocked");
    }

    @Test
    void authenticateAdmin_missingFamily_returnsFailure() {
        when(familyRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = authService.authenticateAdmin("unknown@test.com", "password");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateAdminWithGoogle_verifiedExistingFamily_returnsAdminPayloadAndVerifiesLocalEmail() {
        AuthServiceImpl serviceWithGoogle = createAuthService(
            TestConfigFactory.appConfig(false, null, null, true, true, true, "google-client-id"));
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, false);
        when(googleIdentityVerifier.verify("google-token", "google-client-id"))
            .thenReturn(Optional.of(new GoogleIdentity("user@test.com", true)));
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);
        when(familyRepository.verifyFamily("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = serviceWithGoogle.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        AuthPayload payload = ((OperationResult.Success<AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("fam_1");
        verify(familyRepository).verifyFamily("fam_1");
    }

    @Test
    void authenticateAdminWithGoogle_blockedLinkedFamily_returnsFailure() {
        AuthServiceImpl serviceWithGoogle = createAuthService(
            TestConfigFactory.appConfig(false, null, null, true, true, true, "google-client-id"));
        FamilyEntity family = mockFamily("fam_1", "blocked@test.com", "password123", true, true);
        when(googleIdentityVerifier.verify("google-token", "google-client-id"))
            .thenReturn(Optional.of(new GoogleIdentity("blocked@test.com", true)));
        when(familyRepository.findByEmail("blocked@test.com")).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = serviceWithGoogle.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .contains("Account is blocked");
    }

    @Test
    void authenticateAdminWithGoogle_missingLinkedFamily_returnsFailure() {
        AuthServiceImpl serviceWithGoogle = createAuthService(
            TestConfigFactory.appConfig(false, null, null, false, true, true, "google-client-id"));
        when(googleIdentityVerifier.verify("google-token", "google-client-id"))
            .thenReturn(Optional.of(new GoogleIdentity("missing@test.com", true)));
        when(familyRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = serviceWithGoogle.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .contains("No family account is linked");
    }

    @Test
    void authenticateAdminWithGoogle_disabledConfig_returnsFailureWithoutVerifierCall() {
        OperationResult<AuthPayload> result = authService.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .contains("Google sign-in is not configured");
        verify(googleIdentityVerifier, never()).verify(anyString(), anyString());
    }

    @Test
    void authenticateChild_validToken_returnsChildPayload() {
        ChildEntity child = mock(ChildEntity.class);
        when(child.getId()).thenReturn(10);
        when(child.getFamilyDbId()).thenReturn(1);
        when(child.getName()).thenReturn("Alice");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, true);
        when(childRepository.findByToken("abc123")).thenReturn(Optional.of(child));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = authService.authenticateChild("abc123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<AuthPayload> success3 = (OperationResult.Success<AuthPayload>) result;
        AuthPayload payload = success3.value();
        assertThat(payload).isNotNull();
        assertThat(payload.role()).isEqualTo("child");
        assertThat(payload.childId()).isEqualTo(10);
        assertThat(payload.childName()).isEqualTo("Alice");
    }

    @Test
    void authenticateChild_missingToken_returnsFailure() {
        OperationResult<AuthPayload> result = authService.authenticateChild(null);
        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateChild_invalidToken_returnsFailure() {
        when(childRepository.findByToken("invalid")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = authService.authenticateChild("invalid");
        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void registerFamily_newEmail_returnsSuccess() {
        when(familyRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(familyRepository.create(anyString(), anyString(), anyString(), anyBoolean(), any()))
            .thenAnswer(inv -> Optional.of(mockFamily(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), false, true)));

        OperationResult<AuthPayload> result = authService.registerFamily("new@test.com", "strong123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).create(
            anyString(),
            eq("new@test.com"),
            argThat(hash -> hash.startsWith("$argon2")),
            anyBoolean(),
            any());
    }

    @Test
    void registerFamily_thenAuthenticateAdmin_canLoginImmediately() {
        String email = "flow@test.com";
        String password = "strong123";
        FamilyEntity createdFamily = mockFamily("fam_flow", email, passwordHasher.hash(password), false, true);

        when(familyRepository.findByEmail(email)).thenReturn(Optional.empty(), Optional.of(createdFamily));
        when(familyRepository.create(anyString(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(Optional.of(createdFamily));
        when(familyRepository.updateLastActivity("fam_flow")).thenReturn(true);

        OperationResult<AuthPayload> registerResult = authService.registerFamily(email, password);
        OperationResult<AuthPayload> loginResult = authService.authenticateAdmin(email, password);

        assertThat(registerResult).isInstanceOf(OperationResult.Success.class);
        assertThat(loginResult).isInstanceOf(OperationResult.Success.class);

        AuthPayload payload = ((OperationResult.Success<AuthPayload>) loginResult).value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("fam_flow");
    }

    @Test
    void registerFamily_emailVerificationEnabled_generatesHexVerificationToken() {
        AuthServiceImpl serviceWithVerification = createAuthService(
            TestConfigFactory.appConfig(false, null, null, true, true));
        when(familyRepository.findByEmail("verify@test.com")).thenReturn(Optional.empty());
        when(familyRepository.create(anyString(), anyString(), anyString(), anyBoolean(), any()))
            .thenAnswer(inv -> Optional.of(mockFamily(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), false, false)));

        OperationResult<AuthPayload> result = serviceWithVerification.registerFamily("verify@test.com", "strong123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).create(
            anyString(),
            eq("verify@test.com"),
            argThat(hash -> hash.startsWith("$argon2")),
            eq(false),
            argThat(token -> token instanceof String stringToken
                && stringToken.matches("[0-9a-f]{64}")));
    }

    @Test
    void registerFamily_existingEmail_returnsFailure() {
        FamilyEntity existing = mockFamily("fam_1", "exists@test.com", "password", false, true);
        when(familyRepository.findByEmail("exists@test.com")).thenReturn(Optional.of(existing));

        OperationResult<AuthPayload> result = authService.registerFamily("exists@test.com", "strong123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void registerFamily_weakPassword_returnsFailure() {
        when(familyRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = authService.registerFamily("new@test.com", "aaa");
        assertThat(result).isInstanceOf(OperationResult.Failure.class);

        OperationResult<AuthPayload> result2 = authService.registerFamily("new@test.com", "111111");
        assertThat(result2).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void resetPassword_validToken_updatesPassword() {
        FamilyEntity family = mock(FamilyEntity.class);
        when(family.getFamilyId()).thenReturn("fam_1");
        when(family.getEmail()).thenReturn("user@test.com");
        when(familyRepository.findByResetToken("validtoken")).thenReturn(Optional.of(family));
        when(familyRepository.updatePassword(eq("fam_1"), anyString())).thenReturn(true);
        when(familyRepository.clearResetToken("fam_1")).thenReturn(true);

        OperationResult<Void> result = authService.resetPassword("user@test.com", "validtoken", "newpass123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).updatePassword(eq("fam_1"), argThat(hash -> hash.startsWith("$argon2")));
    }

    @Test
    void resetPassword_invalidToken_returnsFailure() {
        when(familyRepository.findByResetToken("bad")).thenReturn(Optional.empty());

        OperationResult<Void> result = authService.resetPassword("user@test.com", "bad", "newpass123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateAdmin_unverifiedFamilyWithVerificationEnabled_returnsFailure() {
        AuthServiceImpl serviceWithVerification = createAuthService(
            TestConfigFactory.appConfig(false, null, null, true, true));
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, false);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = serviceWithVerification.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure3 = (OperationResult.Failure<?>) result;
        assertThat(failure3.message())
            .contains("Email is not verified");
    }

    @Test
    void authenticateAdmin_wrongSuperAdminPassword_returnsRussianMessageWhenLocaleIsRussian() {
        RequestLocaleHolder.set("ru");

        OperationResult<AuthPayload> result = authService.authenticateAdmin("admin@test.com", "wrong");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure = (OperationResult.Failure<?>) result;
        assertThat(failure.message()).isEqualTo("Неверный пароль администратора");
    }

    @Test
    void forgotPassword_recoveryDisabled_returnsFailure() {
        AuthServiceImpl noRecoveryService = createAuthService(
            TestConfigFactory.appConfig(false, null, null, false, false));

        OperationResult<Void> result = noRecoveryService.forgotPassword("user@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void forgotPassword_missingFamily_returnsSuccessToAvoidDisclosure() {
        when(familyRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        OperationResult<Void> result = authService.forgotPassword("nobody@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void forgotPassword_matchingFamily_generatesHexResetTokenAndExpiry() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, true);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));
        when(familyRepository.setResetToken(eq("fam_1"), anyString(), any())).thenReturn(true);

        OperationResult<Void> result = authService.forgotPassword("user@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).setResetToken(
            eq("fam_1"),
            argThat(token -> token.matches("[0-9a-f]{64}")),
            eq(FIXED_NOW.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    void verifyEmail_validToken_returnsSuccess() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, false);
        when(familyRepository.findByVerificationToken("vtoken")).thenReturn(Optional.of(family));
        when(familyRepository.verifyFamily("fam_1")).thenReturn(true);

        OperationResult<Void> result = authService.verifyEmail("user@test.com", "vtoken");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void verifyEmail_invalidToken_returnsFailure() {
        when(familyRepository.findByVerificationToken("bad")).thenReturn(Optional.empty());

        assertThat(authService.verifyEmail("user@test.com", "bad"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void verifyEmail_emailMismatch_returnsFailure() {
        FamilyEntity family = mockFamily("fam_1", "other@test.com", "password123", false, false);
        when(familyRepository.findByVerificationToken("vtoken")).thenReturn(Optional.of(family));

        assertThat(authService.verifyEmail("user@test.com", "vtoken"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changeAdminPassword_validOldPassword_updatesPassword() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "oldpassword", false, true);
        when(familyRepository.findById("fam_1")).thenReturn(Optional.of(family));
        when(familyRepository.updatePassword(eq("fam_1"), anyString())).thenReturn(true);

        OperationResult<Void> result = authService.changeAdminPassword("fam_1", "oldpassword", "newpassword1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).updatePassword(eq("fam_1"), argThat(hash -> hash.startsWith("$argon2")));
    }

    @Test
    void changeAdminPassword_blankFamilyId_returnsFailure() {
        assertThat(authService.changeAdminPassword("", "old", "new123"))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(authService.changeAdminPassword(null, "old", "new123"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changeAdminPassword_wrongOldPassword_returnsFailure() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "realpassword", false, true);
        when(familyRepository.findById("fam_1")).thenReturn(Optional.of(family));

        assertThat(authService.changeAdminPassword("fam_1", "wrongpassword", "newpassword1"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateChild_blockedFamily_returnsFailure() {
        ChildEntity child = mock(ChildEntity.class);
        when(child.getFamilyDbId()).thenReturn(1);
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password", true, true);
        when(childRepository.findByToken("token")).thenReturn(Optional.of(child));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));

        assertThat(authService.authenticateChild("token"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void registerFamily_repositoryCreateMissing_returnsFailure() {
        when(familyRepository.findByEmail("dup@test.com")).thenReturn(Optional.empty());
        when(familyRepository.create(anyString(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(Optional.empty());

        assertThat(authService.registerFamily("dup@test.com", "strong123"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    private AuthServiceImpl createAuthService(AppConfig config) {
        SuperAdminCredentialsService superAdminCredentialsService = new SuperAdminCredentialsService(
            config,
            passwordHasher,
            superAdminCredentialRepository
        );
        return new AuthServiceImpl(
            familyRepository,
            childRepository,
            config,
            passwordHasher,
            TOKEN_GENERATOR,
            TestConfigFactory.timeProvider(FIXED_NOW),
            superAdminCredentialsService,
            googleIdentityVerifier
        );
    }

    private static FamilyEntity mockFamily(String familyId, String email, String password,
                                            boolean blocked, boolean verified) {
        FamilyEntity entity = mock(FamilyEntity.class);
        when(entity.getFamilyId()).thenReturn(familyId);
        when(entity.getEmail()).thenReturn(email);
        when(entity.getAdminPassword()).thenReturn(password);
        when(entity.isBlocked()).thenReturn(blocked);
        when(entity.isVerified()).thenReturn(verified);
        return entity;
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
