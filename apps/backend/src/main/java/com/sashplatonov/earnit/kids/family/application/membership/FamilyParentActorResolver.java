package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;

import java.util.Optional;

final class FamilyParentActorResolver {

    private FamilyParentActorResolver() {
    }

    static Optional<FamilyParentMembershipEntity> resolve(
        Integer familyDbId,
        Integer actorParentAccountId,
        String actorEmail,
        ParentAccountRepository parentAccountRepository,
        FamilyParentMembershipRepository membershipRepository) {
        return Optional.ofNullable(actorParentAccountId)
            .map(accountId -> membershipRepository.findByParentAndFamily(accountId, familyDbId))
            .orElseGet(() -> Optional.ofNullable(actorEmail)
                .filter(email -> !email.isBlank())
                .flatMap(parentAccountRepository::findByEmail)
                .flatMap(parent -> membershipRepository.findByParentAndFamily(parent.getId(), familyDbId)));
    }
}
