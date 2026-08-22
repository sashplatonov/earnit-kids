package com.sashplatonov.earnit.kids.service.auth;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
class AuthChildAuthService {
    private final ChildRepository childRepository;
    private final FamilyRepository familyRepository;

    OperationResult<AuthPayload> authenticateChild(String childToken) {
        if (childToken == null || childToken.isBlank()) {
            return OperationResult.failure(BackendMessages.message("auth.tokenMissing"));
        }

        var childOpt = childRepository.findByToken(childToken);
        if (childOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidLink"));
        }

        var child = childOpt.get();
        var familyOpt = familyRepository.findByDbId(child.getFamilyDbId());
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        FamilyEntity family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }

        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), family.getEmail(), "child",
                child.getId(), child.getName(), "child", null, false));
    }
}
