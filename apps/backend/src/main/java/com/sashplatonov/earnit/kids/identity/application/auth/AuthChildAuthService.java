package com.sashplatonov.earnit.kids.identity.application.auth;

import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.family.application.invitation.ChildMagicLinkInvitationService;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class AuthChildAuthService {
    private final ChildMagicLinkInvitationService childInvitationService;
    private final ChildRepository legacyChildRepository;
    private final FamilyRepository legacyFamilyRepository;

    @Inject
    AuthChildAuthService(ChildMagicLinkInvitationService childInvitationService) {
        this.childInvitationService = childInvitationService;
        this.legacyChildRepository = null;
        this.legacyFamilyRepository = null;
    }

    AuthChildAuthService(ChildRepository childRepository, FamilyRepository familyRepository) {
        this.childInvitationService = null;
        this.legacyChildRepository = childRepository;
        this.legacyFamilyRepository = familyRepository;
    }

    OperationResult<AuthPayload> authenticateChild(String childToken) {
        if (childInvitationService != null) return childInvitationService.consume(childToken);
        if (childToken == null || childToken.isBlank()) return OperationResult.failure("tokenMissing");
        var child = legacyChildRepository.findByToken(childToken).orElse(null);
        if (child == null) return OperationResult.failure("invalidLink");
        var family = legacyFamilyRepository.findByDbId(child.getFamilyDbId()).orElse(null);
        if (family == null || family.isBlocked()) return OperationResult.failure("invalidLink");
        return OperationResult.success(new AuthPayload(family.getFamilyId(), family.getEmail(), "child",
            child.getId(), child.getName(), "child", null, false));
    }
}
