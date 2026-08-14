package com.sashplatonov.earnit.kids.service.account;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.dto.response.AccountConnectionResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramAccountConnectionResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.service.telegram.TelegramAccountConnectionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock FamilyRepository families;
    @Mock ParentAccountRepository parents;
    @Mock TelegramAccountConnectionService telegramConnections;
    @Mock SecureTokenGenerator tokens;

    @InjectMocks AccountServiceImpl service;

    private FamilyEntity family(String email) {
        return FamilyEntity.builder().id(1).familyId("family-1").email(email).adminPassword("hash").build();
    }

    @Test
    void connection_reportsEmailLinkedAndTelegramState() {
        when(families.findById("family-1")).thenReturn(Optional.of(family("parent@example.test")));
        when(telegramConnections.connection("family-1", "parent@example.test"))
            .thenReturn(OperationResult.success(
                new TelegramAccountConnectionResponse("parent@example.test", true, true, "url")));

        OperationResult<AccountConnectionResponse> result = service.connection("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var value = ((OperationResult.Success<AccountConnectionResponse>) result).value();
        assertThat(value.email()).isEqualTo("parent@example.test");
        assertThat(value.emailLinked()).isTrue();
        assertThat(value.telegramLinked()).isTrue();
    }

    @Test
    void connection_reportsTelegramUnlinkedWhenConnectionFails() {
        when(families.findById("family-1")).thenReturn(Optional.of(family("parent@example.test")));
        when(telegramConnections.connection("family-1", "parent@example.test"))
            .thenReturn(OperationResult.failure("TG_ERROR", "tg.error"));

        OperationResult<AccountConnectionResponse> result = service.connection("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var value = ((OperationResult.Success<AccountConnectionResponse>) result).value();
        assertThat(value.telegramLinked()).isFalse();
    }

    @Test
    void connection_reportsEmailUnlinkedWhenFamilyEmailMissing() {
        FamilyEntity telegramOnly = FamilyEntity.builder().id(1).familyId("family-1").email(null).adminPassword("hash").build();
        when(families.findById("family-1")).thenReturn(Optional.of(telegramOnly));
        when(telegramConnections.connection("family-1", "parent@example.test"))
            .thenReturn(OperationResult.failure("TG_ERROR", "tg.error"));

        OperationResult<AccountConnectionResponse> result = service.connection("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var value = ((OperationResult.Success<AccountConnectionResponse>) result).value();
        assertThat(value.emailLinked()).isFalse();
    }

    @Test
    void changeEmail_rejectsBlankAndInvalidValues() {
        assertThat(service.changeEmail("family-1", "a@b.c", "")).isInstanceOf(OperationResult.Failure.class);
        assertThat(service.changeEmail("family-1", "a@b.c", "   ")).isInstanceOf(OperationResult.Failure.class);
        assertThat(service.changeEmail("family-1", "a@b.c", "x".repeat(300))).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changeEmail_rejectsUnchangedEmail() {
        OperationResult<Void> result = service.changeEmail("family-1", "parent@example.test", "parent@example.test");
        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void changeEmail_rejectsEmailRegisteredElsewhere() {
        when(families.findByEmail("taken@example.test")).thenReturn(Optional.of(family("taken@example.test")));

        OperationResult<Void> result = service.changeEmail("family-1", "parent@example.test", "taken@example.test");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        verify(families, never()).updateEmail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changeEmail_updatesFamilyAndParentWhenFree() {
        when(families.findByEmail("new@example.test")).thenReturn(Optional.empty());
        when(parents.findByEmail("new@example.test")).thenReturn(Optional.empty());
        when(parents.findByEmail("parent@example.test")).thenReturn(Optional.of(org.mockito.Mockito.mock(
            com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity.class)));
        when(families.updateEmail("family-1", "new@example.test")).thenReturn(true);
        when(parents.changeEmail("parent@example.test", "new@example.test")).thenReturn(true);

        OperationResult<Void> result = service.changeEmail("family-1", "parent@example.test", "new@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(families).updateEmail("family-1", "new@example.test");
        verify(parents).changeEmail("parent@example.test", "new@example.test");
    }

    @Test
    void changeEmail_failsWhenParentAccountMissing() {
        when(families.findByEmail("new@example.test")).thenReturn(Optional.empty());
        when(parents.findByEmail("new@example.test")).thenReturn(Optional.empty());
        when(parents.findByEmail("parent@example.test")).thenReturn(Optional.empty());

        OperationResult<Void> result = service.changeEmail("family-1", "parent@example.test", "new@example.test");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        verify(families, never()).updateEmail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkEmail_requiresLinkedTelegram() {
        when(telegramConnections.connection("family-1", "parent@example.test"))
            .thenReturn(OperationResult.failure("TG_ERROR", "tg.error"));

        OperationResult<Void> result = service.unlinkEmail("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        verify(families, never()).updatePassword(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unlinkEmail_wipePasswordsWhenTelegramLinked() {
        when(telegramConnections.connection("family-1", "parent@example.test"))
            .thenReturn(OperationResult.success(
                new TelegramAccountConnectionResponse("parent@example.test", true, true, "url")));
        when(parents.findByEmail("parent@example.test")).thenReturn(Optional.of(org.mockito.Mockito.mock(
            com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity.class)));
        when(families.updatePassword("family-1", "unusable-hash")).thenReturn(true);
        when(parents.disablePasswordLogin("parent@example.test", "unusable-hash")).thenReturn(true);
        when(tokens.generateHexToken(32)).thenReturn("unusable-hash");

        OperationResult<Void> result = service.unlinkEmail("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(families).updatePassword("family-1", "unusable-hash");
        verify(parents).disablePasswordLogin("parent@example.test", "unusable-hash");
    }
}
