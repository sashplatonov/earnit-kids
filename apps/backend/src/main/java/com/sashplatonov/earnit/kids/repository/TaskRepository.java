package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.sashplatonov.earnit.kids.repository.command.TaskUpsertCommand;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TaskRepository implements PanacheRepositoryBase<TaskEntity, Long> {
    private final SlowOperationDiagnostics slowOperationDiagnostics;

    public List<TaskEntity> findByFamilyAndChildAndTaskIds(int familyDbId, Collection<Integer> childIds,
                                                           Collection<Long> taskIds) {
        if (childIds == null || childIds.isEmpty() || taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return find("familyId = ?1 AND childId IN ?2 AND taskId IN ?3 ORDER BY id DESC",
            familyDbId, childIds, taskIds).list();
    }

    public List<TaskEntity> getTasks(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getTasks",
            () -> list("childId = ?1 AND deleted = false ORDER BY id ASC", childId),
            "childId",
            String.valueOf(childId)
        );
    }

    public List<TaskEntity> getTasksForFamily(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getTasksForFamily",
            () -> list("familyId = ?1 AND deleted = false ORDER BY id ASC", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public void markAllTasksDeleted(int childId) {
        update("deleted = true where childId = ?1", childId);
    }

    @Transactional
    public boolean upsertTask(TaskUpsertCommand command) {
        Optional<TaskEntity> existing = find("childId = ?1 AND taskId = ?2", command.childId(), command.taskId())
            .firstResultOptional();
        if (existing.isPresent()) {
            TaskEntity task = existing.get();
            task.setName(command.name());
            task.setCoins(command.coins());
            task.setGroupName(command.groupName());
            task.setFrequency(command.frequency());
            task.setComment(command.comment());
            task.setMoneyLimit(command.moneyLimit());
            task.setActive(command.active());
            task.setDeleted(command.deleted());
        } else {
            persist(TaskEntity.builder()
                .familyId(command.familyDbId())
                .childId(command.childId())
                .taskId(command.taskId())
                .name(command.name())
                .coins(command.coins())
                .groupName(command.groupName())
                .frequency(command.frequency())
                .comment(command.comment())
                .moneyLimit(command.moneyLimit())
                .active(command.active())
                .deleted(command.deleted())
                .build());
        }
        return true;
    }

    @Transactional
    public boolean softDeleteTask(int childId, long taskId) {
        return find("childId = ?1 AND taskId = ?2", childId, taskId)
            .firstResultOptional()
            .map(task -> {
                task.setDeleted(true);
                return true;
            })
            .orElse(false);
    }
}
