package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class TaskRepository implements PanacheRepositoryBase<TaskEntity, Long> {

    public List<TaskEntity> findByFamilyAndChildAndTaskIds(int familyDbId, Collection<Integer> childIds,
                                                           Collection<Long> taskIds) {
        if (childIds == null || childIds.isEmpty() || taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return find("familyId = ?1 AND childId IN ?2 AND taskId IN ?3 ORDER BY id DESC",
            familyDbId, childIds, taskIds).list();
    }
}
