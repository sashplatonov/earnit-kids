package com.sashplatonov.earnit.kids.platform.webpush;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class WebPushSubscriptionRepository implements PanacheRepositoryBase<WebPushSubscriptionEntity, Long> {
    public Optional<WebPushSubscriptionEntity> findByEndpoint(String endpoint) {
        return find("endpoint", endpoint).firstResultOptional();
    }
    public List<WebPushSubscriptionEntity> findParents(int familyId) {
        return find("familyId = ?1 and actorType = 'parent'", familyId).list();
    }
    public List<WebPushSubscriptionEntity> findChild(int familyId, int childId) {
        return find("familyId = ?1 and actorType = 'child' and childId = ?2", familyId, childId).list();
    }
    public void deleteForActor(String endpoint, int familyId, String actorType, Integer parentId, Integer childId) {
        if (parentId == null) {
            delete("endpoint = ?1 and familyId = ?2 and actorType = ?3 and parentAccountId is null and childId = ?4",
                endpoint, familyId, actorType, childId);
        } else {
            delete("endpoint = ?1 and familyId = ?2 and actorType = ?3 and parentAccountId = ?4 and childId = ?5",
                endpoint, familyId, actorType, parentId, childId);
        }
    }
}
