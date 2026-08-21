package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;

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
