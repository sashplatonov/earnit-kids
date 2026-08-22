package com.sashplatonov.earnit.kids.family.infrastructure.persistence.notification;

import com.sashplatonov.earnit.kids.family.domain.model.notification.FamilyNotificationPreferenceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FamilyNotificationPreferenceRepository
    implements PanacheRepositoryBase<FamilyNotificationPreferenceEntity, Integer> {

    public List<FamilyNotificationPreferenceEntity> findByFamily(int familyDbId) {
        return list("familyId = ?1 ORDER BY scope, childId, prefKey", familyDbId);
    }

    public Optional<FamilyNotificationPreferenceEntity> findOne(int familyDbId, String scope,
                                                                Integer childId, String prefKey) {
        if (childId == null) {
            return find("familyId = ?1 AND scope = ?2 AND childId IS NULL AND prefKey = ?3",
                familyDbId, scope, prefKey).firstResultOptional();
        }
        return find("familyId = ?1 AND scope = ?2 AND childId = ?3 AND prefKey = ?4",
            familyDbId, scope, childId, prefKey).firstResultOptional();
    }

    @Transactional
    public void setEnabled(int familyDbId, String scope, Integer childId, String prefKey, boolean enabled) {
        Optional<FamilyNotificationPreferenceEntity> existing = findOne(familyDbId, scope, childId, prefKey);
        if (existing.isPresent()) {
            existing.get().setEnabled(enabled);
            existing.get().setUpdatedAt(Instant.now());
            return;
        }
        persist(FamilyNotificationPreferenceEntity.builder()
            .familyId(familyDbId)
            .scope(scope)
            .childId(childId)
            .prefKey(prefKey)
            .enabled(enabled)
            .updatedAt(Instant.now())
            .build());
    }
}
