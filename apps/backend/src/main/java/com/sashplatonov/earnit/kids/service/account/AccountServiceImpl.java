package com.sashplatonov.earnit.kids.service.account;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.dto.response.AccountConnectionResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramAccountConnectionResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.service.telegram.TelegramAccountConnectionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Optional;

// EXPLAIN: Parent "My Account" operations. Email actions live only inside the
// EXPLAIN: nested Email settings; unlinking requires a working Telegram login.
@ApplicationScoped
public class AccountServiceImpl implements AccountService {
    @Inject private FamilyRepository families;
    @Inject private ParentAccountRepository parents;
    @Inject private TelegramAccountConnectionService telegramConnections;
    @Inject private SecureTokenGenerator tokens;

    AccountServiceImpl() {
    }

    @Override
    public OperationResult<AccountConnectionResponse> connection(String familyId, String email) {
        OperationResult<TelegramAccountConnectionResponse> telegram = telegramConnections.connection(familyId, email);
        boolean telegramLinked = telegram instanceof OperationResult.Success<TelegramAccountConnectionResponse> success
            && success.value().telegramConnected();
        Optional<FamilyEntity> family = families.findById(familyId);
        if (family.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        return OperationResult.success(new AccountConnectionResponse(
            family.get().getEmail(), true, telegramLinked));
    }

    @Override
    public OperationResult<Void> changeEmail(String familyId, String currentEmail, String newEmail) {
        if (newEmail == null || newEmail.isBlank() || newEmail.length() > 254) {
            return failure("INVALID_EMAIL", "auth.emailInvalid");
        }
        String normalized = newEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.equalsIgnoreCase(currentEmail)) {
            return failure("EMAIL_UNCHANGED", "auth.emailUnchanged");
        }
        if (families.findByEmail(normalized).isPresent() || parents.findByEmail(normalized).isPresent()) {
            return failure("EMAIL_TAKEN", "auth.emailRegistered");
        }

        boolean familyUpdated = families.updateEmail(familyId, normalized);
        if (!familyUpdated) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        parents.changeEmail(currentEmail, normalized);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> unlinkEmail(String familyId, String email) {
        OperationResult<TelegramAccountConnectionResponse> telegram = telegramConnections.connection(familyId, email);
        boolean telegramLinked = telegram instanceof OperationResult.Success<TelegramAccountConnectionResponse> success
            && success.value().telegramConnected();
        if (!telegramLinked) {
            return failure("EMAIL_UNLINK_REQUIRES_TELEGRAM", "account.emailUnlinkRequiresTelegram");
        }

        String unusable = tokens.generateHexToken(32);
        families.updatePassword(familyId, unusable);
        parents.disablePasswordLogin(email, unusable);
        return OperationResult.success(null);
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }
}
