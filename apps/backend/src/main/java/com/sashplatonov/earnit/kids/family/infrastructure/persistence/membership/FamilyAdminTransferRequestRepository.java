package com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership;

import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyAdminTransferRequestEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FamilyAdminTransferRequestRepository
    implements PanacheRepositoryBase<FamilyAdminTransferRequestEntity, Integer> {

    public Optional<FamilyAdminTransferRequestEntity> findPendingByFamily(Integer familyId) {
        return find("familyId = ?1 AND status = 'pending'", familyId).firstResultOptional();
    }

    public List<FamilyAdminTransferRequestEntity> findByTarget(Integer targetMembershipId) {
        return find("targetMembershipId = ?1 ORDER BY createdAt DESC", targetMembershipId).list();
    }

    public Optional<FamilyAdminTransferRequestEntity> findPendingByTarget(Integer targetMembershipId) {
        return find("targetMembershipId = ?1 AND status = 'pending'", targetMembershipId)
            .firstResultOptional();
    }

    public List<FamilyAdminTransferRequestEntity> findPendingByFamilyAll(Integer familyId) {
        return find("familyId = ?1 AND status = 'pending'", familyId).list();
    }
}
