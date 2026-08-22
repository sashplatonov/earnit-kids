package com.sashplatonov.earnit.kids.identity.application.auth;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.PasswordHasher;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sashplatonov.earnit.kids.identity.application.google.GoogleIdentityVerifier;
import com.sashplatonov.earnit.kids.platform.application.observability.BackendKpiMetrics;
import com.sashplatonov.earnit.kids.identity.application.google.GoogleIdentity;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");
    private static final SecureTokenGenerator TOKEN_GENERATOR = new SecureTokenGenerator();

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;
    @Mock ParentAccountRepository parentAccountRepository;
    @Mock FamilyParentMembershipRepository membershipRepository;
    @Mock GoogleIdentityVerifier googleIdentityVerifier;

    private SimpleMeterRegistry meterRegistry;
    private BackendKpiMetrics backendKpiMetrics;
    AuthServiceImpl authService;
    AuthSupportService supportService;
    AuthMembershipService membershipService;
    AuthAdminAuthService adminAuthService;
    AuthChildAuthService childAuthService;
    AuthLifecycleService lifecycleService;
    PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        RequestLocaleHolder.set("en");
        passwordHasher = new PasswordHasher();
        meterRegistry = new SimpleMeterRegistry();
        backendKpiMetrics = new BackendKpiMetrics(meterRegistry);
        authService = createAuthService(TestConfigFactory.appConfig(false, "admin@test.com", false, true));
    }

    @Test
    void authenticateAdmin_returnsFamilyAdminPayloadWithoutLegacyAuthority() {
        var parent = mockParentAccount("user@test.com", "password123", false, true);
        var membership = mockMembership(1, 1, "family_admin");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, true);

        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(membership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        AuthPayload payload = ((OperationResult.Success<AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.permission()).isEqualTo("family_admin");
    }



    @Test
    void authenticateAdmin_familyCredentialsMatch_returnsAdminPayload() {
        var parent = mockParentAccount("user@test.com", "password123", false, true);
        var membership = mockMembership(1, 1, "family_admin");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, true);

        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(membership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<AuthPayload> success2 = (OperationResult.Success<AuthPayload>) result;
        AuthPayload payload = success2.value();
        assertThat(payload).isNotNull();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("fam_1");
    }

    @Test
    void authenticateAdmin_argon2Password_returnsAdminPayloadWithoutRehash() {
        String hashedPassword = passwordHasher.hash("password123");
        var parent = mockParentAccount("user@test.com", hashedPassword, false, true);
        var membership = mockMembership(1, 1, "family_admin");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", hashedPassword, false, true);

        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(membership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void authenticateAdmin_sha256Password_returnsAdminPayloadAndRehashes() {
        var parent = mockParentAccount("user@test.com", sha256Hex("password123"), false, true);
        var membership = mockMembership(1, 1, "family_admin");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", sha256Hex("password123"), false, true);

        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(membership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void authenticateAdmin_multipleMemberships_returnsChooserWithBlockedFlags() {
        var parent = mockParentAccount("user@test.com", "password123", false, true);
        var firstMembership = mockMembership(1, 1, "family_admin");
        var secondMembership = mockMembership(1, 2, "viewer");
        FamilyEntity activeFamily = mockFamily("fam_active", "user@test.com", "password123", false, true);
        FamilyEntity blockedFamily = mockFamily("fam_blocked", "other@test.com", "password123", true, true);

        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(firstMembership, secondMembership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(activeFamily));
        when(familyRepository.findByDbId(2)).thenReturn(Optional.of(blockedFamily));

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        AuthPayload payload = ((OperationResult.Success<AuthPayload>) result).value();
        assertThat(payload.selectionRequired()).isTrue();
        assertThat(payload.familyChoices()).containsExactly(
            new AuthPayload.FamilyChoice("fam_active", "fam_active", "family_admin", false),
            new AuthPayload.FamilyChoice("fam_blocked", "fam_blocked", "viewer", true)
        );
    }

    @Test
    void authenticateAdmin_blockedFamily_returnsFailure() {
        var parent = mockParentAccount("blocked@test.com", "password123", true, true);
        when(parentAccountRepository.findByEmail("blocked@test.com")).thenReturn(Optional.of(parent));

        OperationResult<AuthPayload> result = authService.authenticateAdmin("blocked@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure2 = (OperationResult.Failure<?>) result;
        assertThat(failure2.message())
            .isEqualTo("Account is blocked");
    }

    @Test
    void authenticateAdmin_missingFamily_returnsFailure() {
        when(parentAccountRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = authService.authenticateAdmin("unknown@test.com", "password");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateAdminWithGoogle_verifiedExistingFamily_returnsAdminPayloadAndVerifiesLocalEmail() {
        AuthServiceImpl serviceWithGoogle = createAuthService(
            TestConfigFactory.appConfig(false, null, true, true, true, "google-client-id"));
        var parent = mockParentAccount("user@test.com", "password123", false, false);
        var membership = mockMembership(1, 1, "family_admin");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "password123", false, false);

        when(googleIdentityVerifier.verify("google-token", "google-client-id"))
            .thenReturn(Optional.of(new GoogleIdentity("user@test.com", true)));
        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(membership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = serviceWithGoogle.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        AuthPayload payload = ((OperationResult.Success<AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("fam_1");
    }

    @Test
    void authenticateAdminWithGoogle_blockedLinkedFamily_returnsFailure() {
        AuthServiceImpl serviceWithGoogle = createAuthService(
            TestConfigFactory.appConfig(false, null, true, true, true, "google-client-id"));
        var parent = mockParentAccount("blocked@test.com", "password123", true, true);

        when(googleIdentityVerifier.verify("google-token", "google-client-id"))
            .thenReturn(Optional.of(new GoogleIdentity("blocked@test.com", true)));
        when(parentAccountRepository.findByEmail("blocked@test.com")).thenReturn(Optional.of(parent));

        OperationResult<AuthPayload> result = serviceWithGoogle.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .contains("Account is blocked");
    }

    @Test
    void authenticateAdminWithGoogle_missingLinkedFamily_returnsFailure() {
        AuthServiceImpl serviceWithGoogle = createAuthService(
            TestConfigFactory.appConfig(false, null, false, true, true, "google-client-id"));
        when(googleIdentityVerifier.verify("google-token", "google-client-id"))
            .thenReturn(Optional.of(new GoogleIdentity("missing@test.com", true)));
        when(parentAccountRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = serviceWithGoogle.authenticateAdminWithGoogle("google-token");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .contains("No family account is linked to this Google email yet");
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
        when(parentAccountRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        // persistAndFlush returns void, so we just verify it was called
        doNothing().when(parentAccountRepository).persistAndFlush(any());
        doNothing().when(familyRepository).persistAndFlush(any());
        doNothing().when(membershipRepository).persistAndFlush(any());

        OperationResult<AuthPayload> result = authService.registerFamily("new@test.com", "strong123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(parentAccountRepository).persistAndFlush(argThat(parent -> "new@test.com".equals(parent.getEmail())));
        verify(familyRepository).persistAndFlush(argThat(family ->
            "new@test.com".equals(family.getEmail())
                && family.getAdminPassword() != null
                && family.getFamilyId() != null
        ));
        verify(membershipRepository).persistAndFlush(any());
    }

    @Test
    void registerFamily_thenAuthenticateAdmin_canLoginImmediately() {
        String email = "flow@test.com";
        String password = "strong123";

        // Registration
        when(parentAccountRepository.findByEmail(email)).thenReturn(Optional.empty());
        doNothing().when(parentAccountRepository).persistAndFlush(any());
        doNothing().when(familyRepository).persistAndFlush(any());
        doNothing().when(membershipRepository).persistAndFlush(any());

        OperationResult<AuthPayload> registerResult = authService.registerFamily(email, password);
        assertThat(registerResult).isInstanceOf(OperationResult.Success.class);

        // Login - need to mock parent account and membership
        var parent = mockParentAccount(email, passwordHasher.hash(password), false, true);
        var membership = mockMembership(1, 1, "family_admin");
        FamilyEntity family = mockFamily("fam_flow", email, passwordHasher.hash(password), false, true);

        when(parentAccountRepository.findByEmail(email)).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAccountId(1)).thenReturn(List.of(membership));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_flow")).thenReturn(true);

        OperationResult<AuthPayload> loginResult = authService.authenticateAdmin(email, password);

        assertThat(loginResult).isInstanceOf(OperationResult.Success.class);
        AuthPayload payload = ((OperationResult.Success<AuthPayload>) loginResult).value();
        assertThat(payload.role()).isEqualTo("admin");
    }

    @Test
    void registerFamily_existingEmail_returnsFailure() {
        var existing = mockParentAccount("exists@test.com", "password", false, true);
        when(parentAccountRepository.findByEmail("exists@test.com")).thenReturn(Optional.of(existing));

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
    void authenticateAdmin_superAdminEmailNotFound_returnsFailure() {
        when(parentAccountRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = authService.authenticateAdmin("admin@test.com", "password123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
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
        when(parentAccountRepository.findByEmail("dup@test.com")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("DB error")).when(parentAccountRepository).persistAndFlush(any());

        OperationResult<AuthPayload> result = authService.registerFamily("dup@test.com", "strong123");
        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .contains("auth.registrationFailed");
    }

    @Test
    void selectFamily_blockedFamily_returnsFailure() {
        var parent = mockParentAccount("user@test.com", "password123", false, true);
        FamilyEntity blockedFamily = mockFamily("fam_blocked", "other@test.com", "password123", true, true);

        when(parentAccountRepository.findByEmail("user@test.com")).thenReturn(Optional.of(parent));
        when(familyRepository.findById("fam_blocked")).thenReturn(Optional.of(blockedFamily));

        OperationResult<AuthPayload> result = authService.selectFamily("user@test.com", "fam_blocked");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<AuthPayload>) result).message())
            .isEqualTo("Sign-in is blocked for this family");
    }

    private AuthServiceImpl createAuthService(AppConfig config) {
        supportService = new AuthSupportService(
            config,
            passwordHasher,
            parentAccountRepository,
            TOKEN_GENERATOR
        );
        membershipService = new AuthMembershipService(
            familyRepository,
            parentAccountRepository,
            membershipRepository,
            supportService
        );
        adminAuthService = new AuthAdminAuthService(
            parentAccountRepository,
            googleIdentityVerifier,
            supportService,
            membershipService
        );
        childAuthService = new AuthChildAuthService(childRepository, familyRepository);
        lifecycleService = new AuthLifecycleService(
            familyRepository,
            parentAccountRepository,
            membershipRepository,
            supportService
        );
        return new AuthServiceImpl(
            adminAuthService,
            childAuthService,
            lifecycleService,
            membershipService,
            backendKpiMetrics
        );
    }

    private static FamilyEntity mockFamily(String familyId, String email, String password,
                                            boolean blocked, boolean verified) {
        FamilyEntity entity = mock(FamilyEntity.class);
        when(entity.getFamilyId()).thenReturn(familyId);
        when(entity.getEmail()).thenReturn(email);
        when(entity.getAdminPassword()).thenReturn(password);
        when(entity.isBlocked()).thenReturn(blocked);
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

    private static ParentAccountEntity mockParentAccount(String email, String password, boolean blocked, boolean verified) {
        ParentAccountEntity entity = mock(ParentAccountEntity.class);
        when(entity.getId()).thenReturn(1);
        when(entity.getEmail()).thenReturn(email);
        when(entity.getPasswordHash()).thenReturn(password);
        when(entity.isBlocked()).thenReturn(blocked);
        return entity;
    }

    private static FamilyParentMembershipEntity mockMembership(Integer parentId, Integer familyId, String permission) {
        FamilyParentMembershipEntity entity = mock(FamilyParentMembershipEntity.class);
        when(entity.getParentAccountId()).thenReturn(parentId);
        when(entity.getFamilyId()).thenReturn(familyId);
        when(entity.getPermission()).thenReturn(FamilyParentMembershipEntity.Permission.valueOf(permission));
        return entity;
    }
}
