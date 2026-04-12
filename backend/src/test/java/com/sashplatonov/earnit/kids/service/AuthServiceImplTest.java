package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;

    AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
            familyRepository,
            childRepository,
            TestConfigFactory.appConfig(false, "admin@test.com", "admin123", false, true));
    }

    @Test
    void authenticateAdmin_matchingSuperAdminCredentials_returnsSuperAdminPayload() {
        OperationResult<AuthPayload> result = authService.authenticateAdmin("admin@test.com", "admin123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success1 = (OperationResult.Success<?>) result;
        AuthPayload payload = (AuthPayload) success1.value();
        assertThat(payload.role()).isEqualTo("super_admin");
        assertThat(payload.email()).isEqualTo("admin@test.com");
    }

    @Test
    void authenticateAdmin_wrongSuperAdminPassword_returnsFailure() {
        OperationResult<AuthPayload> result = authService.authenticateAdmin("admin@test.com", "wrong");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure1 = (OperationResult.Failure<?>) result;
        assertThat(failure1.message())
            .contains("Неверный пароль");
    }

    @Test
    void authenticateAdmin_familyCredentialsMatch_returnsAdminPayload() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "pin123", false, true);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));
        when(familyRepository.updateLastActivity("fam_1")).thenReturn(true);

        OperationResult<AuthPayload> result = authService.authenticateAdmin("user@test.com", "pin123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success2 = (OperationResult.Success<?>) result;
        AuthPayload payload = (AuthPayload) success2.value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("fam_1");
    }

    @Test
    void authenticateAdmin_blockedFamily_returnsFailure() {
        FamilyEntity family = mockFamily("fam_1", "blocked@test.com", "pin123", true, true);
        when(familyRepository.findByEmail("blocked@test.com")).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = authService.authenticateAdmin("blocked@test.com", "pin123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure2 = (OperationResult.Failure<?>) result;
        assertThat(failure2.message())
            .isEqualTo("Аккаунт заблокирован");
    }

    @Test
    void authenticateAdmin_missingFamily_returnsFailure() {
        when(familyRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        OperationResult<AuthPayload> result = authService.authenticateAdmin("unknown@test.com", "pin");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateChild_validToken_returnsChildPayload() {
        ChildEntity child = mock(ChildEntity.class);
        when(child.getId()).thenReturn(10);
        when(child.getFamilyDbId()).thenReturn(1);
        when(child.getName()).thenReturn("Alice");
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "pin123", false, true);
        when(childRepository.findByToken("abc123")).thenReturn(Optional.of(child));
        when(familyRepository.findByDbId(1)).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = authService.authenticateChild("abc123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success3 = (OperationResult.Success<?>) result;
        AuthPayload payload = (AuthPayload) success3.value();
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
    }

    @Test
    void registerFamily_existingEmail_returnsFailure() {
        FamilyEntity existing = mockFamily("fam_1", "exists@test.com", "pin", false, true);
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
        when(familyRepository.updatePassword("fam_1", "newpass123")).thenReturn(true);
        when(familyRepository.clearResetToken("fam_1")).thenReturn(true);

        OperationResult<Void> result = authService.resetPassword("user@test.com", "validtoken", "newpass123");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void resetPassword_invalidToken_returnsFailure() {
        when(familyRepository.findByResetToken("bad")).thenReturn(Optional.empty());

        OperationResult<Void> result = authService.resetPassword("user@test.com", "bad", "newpass123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateAdmin_unverifiedFamilyWithVerificationEnabled_returnsFailure() {
        AuthServiceImpl serviceWithVerification = new AuthServiceImpl(
            familyRepository,
            childRepository,
            TestConfigFactory.appConfig(false, null, null, true, true));
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "pin123", false, false);
        when(familyRepository.findByEmail("user@test.com")).thenReturn(Optional.of(family));

        OperationResult<AuthPayload> result = serviceWithVerification.authenticateAdmin("user@test.com", "pin123");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<?> failure3 = (OperationResult.Failure<?>) result;
        assertThat(failure3.message())
            .contains("Email не подтвержден");
    }

    @Test
    void forgotPassword_recoveryDisabled_returnsFailure() {
        AuthServiceImpl noRecoveryService = new AuthServiceImpl(
            familyRepository,
            childRepository,
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
    void verifyEmail_validToken_returnsSuccess() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "pin123", false, false);
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
        FamilyEntity family = mockFamily("fam_1", "other@test.com", "pin123", false, false);
        when(familyRepository.findByVerificationToken("vtoken")).thenReturn(Optional.of(family));

        assertThat(authService.verifyEmail("user@test.com", "vtoken"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changeAdminPin_validOldPin_updatesPassword() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "oldpin", false, true);
        when(familyRepository.findById("fam_1")).thenReturn(Optional.of(family));
        when(familyRepository.updatePassword("fam_1", "newpin1")).thenReturn(true);

        OperationResult<Void> result = authService.changeAdminPin("fam_1", "oldpin", "newpin1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void changeAdminPin_blankFamilyId_returnsFailure() {
        assertThat(authService.changeAdminPin("", "old", "new123"))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(authService.changeAdminPin(null, "old", "new123"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changeAdminPin_wrongOldPin_returnsFailure() {
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "realpin", false, true);
        when(familyRepository.findById("fam_1")).thenReturn(Optional.of(family));

        assertThat(authService.changeAdminPin("fam_1", "wrongpin", "newpin1"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void authenticateChild_blockedFamily_returnsFailure() {
        ChildEntity child = mock(ChildEntity.class);
        when(child.getFamilyDbId()).thenReturn(1);
        FamilyEntity family = mockFamily("fam_1", "user@test.com", "pin", true, true);
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
}
