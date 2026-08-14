package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.List;

@ApplicationScoped
public class TelegramIdentityRepository implements PanacheRepositoryBase<TelegramIdentityEntity, Integer> {
    public Optional<TelegramIdentityEntity> findActiveByTelegramUserId(long telegramUserId) {
        return find("telegramUserId = ?1 AND active = true", telegramUserId).firstResultOptional();
    }
    public Optional<TelegramIdentityEntity> findActiveParent(Integer familyId) {
        return find("familyId = ?1 AND role = 'parent' AND active = true", familyId).firstResultOptional();
    }
    public Optional<TelegramIdentityEntity> findActiveParentByParentAccountId(Integer parentAccountId) {
        return find("parentAccountId = ?1 AND role = 'parent' AND active = true", parentAccountId)
            .firstResultOptional();
    }
    public Optional<TelegramIdentityEntity> findActiveParentByIdAndFamilyId(Integer identityId, Integer familyId) {
        return find("id = ?1 AND familyId = ?2 AND role = 'parent' AND active = true", identityId, familyId)
            .firstResultOptional();
    }
    public Optional<TelegramIdentityEntity> findActiveChild(Integer childId) {
        return find("childId = ?1 AND role = 'child' AND active = true", childId).firstResultOptional();
    }
    public List<TelegramIdentityEntity> findActiveParents(Integer familyId) {
        return find("familyId = ?1 AND role = 'parent' AND active = true", familyId).list();
    }
}
