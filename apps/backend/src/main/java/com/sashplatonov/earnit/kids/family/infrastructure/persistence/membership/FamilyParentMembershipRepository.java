package com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership;

import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FamilyParentMembershipRepository implements PanacheRepositoryBase<FamilyParentMembershipEntity, Integer> {

    public List<FamilyParentMembershipEntity> findByParentAccountId(Integer parentAccountId) {
        return find("parentAccountId = ?1 AND status = 'active'", parentAccountId).list();
    }

    public List<FamilyParentMembershipEntity> findByFamilyId(Integer familyId) {
        return find("familyId = ?1 AND status = 'active'", familyId).list();
    }

    public List<FamilyParentMembershipEntity> findByFamilyIdIncludingInactive(Integer familyId) {
        return find("familyId = ?1", familyId).list();
    }

    public Optional<FamilyParentMembershipEntity> findByParentAndFamily(Integer parentAccountId, Integer familyId) {
        return find("parentAccountId = ?1 AND familyId = ?2 AND status = 'active'", parentAccountId, familyId)
            .firstResultOptional();
    }

    public Optional<FamilyParentMembershipEntity> findByParentAndFamilyWithPermission(
            Integer parentAccountId, Integer familyId) {
        return find("parentAccountId = ?1 AND familyId = ?2 AND status = 'active'", parentAccountId, familyId)
            .firstResultOptional();
    }

    public long countFamilyAdmins(Integer familyId) {
        return count("familyId = ?1 AND permission = 'family_admin' AND status = 'active'", familyId);
    }
}
