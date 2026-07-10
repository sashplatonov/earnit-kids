package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FriendRepository implements PanacheRepositoryBase<FriendEntity, Integer> {
    private final SlowOperationDiagnostics slowOperationDiagnostics;

    public List<Integer> getFriendChildIds(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getFriendChildIds",
            () -> find("childId = ?1", childId)
                .stream()
                .map(FriendEntity::getFriendChildId)
                .toList(),
            "childId",
            String.valueOf(childId)
        );
    }

    @Transactional
    public boolean addFriend(int childId, int friendChildId) {
        if (count("childId = ?1 AND friendChildId = ?2", childId, friendChildId) == 0) {
            persist(FriendEntity.builder()
                .childId(childId)
                .friendChildId(friendChildId)
                .build());
        }
        if (count("childId = ?1 AND friendChildId = ?2", friendChildId, childId) == 0) {
            persist(FriendEntity.builder()
                .childId(friendChildId)
                .friendChildId(childId)
                .build());
        }
        return true;
    }
}
