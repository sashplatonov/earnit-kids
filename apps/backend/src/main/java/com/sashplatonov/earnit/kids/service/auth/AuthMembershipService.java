package com.sashplatonov.earnit.kids.service.auth;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
class AuthMembershipService {
    private final FamilyRepository familyRepository;
    private final ParentAccountRepository parentAccountRepository;
    private final FamilyParentMembershipRepository membershipRepository;
    private final AuthSupportService supportService;

    OperationResult<AuthPayload> resolveMembershipAndAuthenticate(String email, ParentAccountEntity parent) {
        var memberships = membershipRepository.findByParentAccountId(parent.getId());
        if (memberships.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.noActiveMemberships"));
        }

        List<AuthPayload.FamilyChoice> choices = buildFamilyChoices(memberships);
        if (memberships.size() == 1) {
            return authenticateWithMembership(email, memberships.get(0));
        }

        return OperationResult.success(
            new AuthPayload(null, email, "admin", null, null, false, null, choices, true));
    }

    OperationResult<AuthPayload> selectFamily(String email, String familyId) {
        var parentOpt = parentAccountRepository.findByEmail(email);
        if (parentOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.invalidCredentials"));
        }

        var parent = parentOpt.get();
        if (parent.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.accountBlocked"));
        }
        var familyOpt = familyRepository.findById(familyId);
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.familyBlocked"));
        }
        var membershipOpt = membershipRepository.findByParentAndFamily(parent.getId(), family.getId());
        if (membershipOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.noActiveMemberships"));
        }

        var membership = membershipOpt.get();
        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = supportService.isSuperAdminEmail(email);
        String permission = membership.getPermission().name();
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), email, "admin", null, null, isSuperAdmin, permission, null, false));
    }

    private List<AuthPayload.FamilyChoice> buildFamilyChoices(List<FamilyParentMembershipEntity> memberships) {
        var choices = new ArrayList<AuthPayload.FamilyChoice>(memberships.size());
        for (var membership : memberships) {
            var familyOpt = familyRepository.findByDbId(membership.getFamilyId());
            if (familyOpt.isEmpty()) {
                continue;
            }

            FamilyEntity family = familyOpt.get();
            choices.add(new AuthPayload.FamilyChoice(
                family.getFamilyId(),
                family.getFamilyId(),
                membership.getPermission().name(),
                family.isBlocked()));
        }
        return choices;
    }

    private OperationResult<AuthPayload> authenticateWithMembership(String email,
                                                                    FamilyParentMembershipEntity membership) {
        var familyOpt = familyRepository.findByDbId(membership.getFamilyId());
        if (familyOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("auth.familyNotFound"));
        }

        var family = familyOpt.get();
        if (family.isBlocked()) {
            return OperationResult.failure(BackendMessages.message("auth.familyBlocked"));
        }

        familyRepository.updateLastActivity(family.getFamilyId());
        boolean isSuperAdmin = supportService.isSuperAdminEmail(email);
        String permission = membership.getPermission().name();
        return OperationResult.success(
            new AuthPayload(family.getFamilyId(), email, "admin", null, null, isSuperAdmin, permission, null, false));
    }
}
