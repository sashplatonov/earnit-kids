package com.sashplatonov.earnit.kids.service.family.command;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.command.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.repository.command.TaskContentCommand;
import com.sashplatonov.earnit.kids.repository.command.TaskUpsertCommand;

import java.util.Map;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
final class FamilyCommandMutationService {
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final FamilyCommandPayloadService payloadService;

    @Inject
    FamilyCommandMutationService(FamilyRepository familyRepository,
                                 ChildRepository childRepository,
                                 TaskRepository taskRepository,
                                 ShopItemRepository shopItemRepository,
                                 FamilyCommandPayloadService payloadService) {
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
        this.payloadService = payloadService;
    }

    void syncFamilyRules(String familyId, Map<String, Object> payload, boolean adminSession) {
        if (!adminSession || !payload.containsKey("rules")) {
            return;
        }

        familyRepository.updateRules(familyId, payloadService.asNullableString(payload.get("rules")));
    }

    void syncTasks(int familyDbId, Integer selectedChildId, Map<String, Object> payload) {
        if (selectedChildId == null || !payload.containsKey("tasks")) {
            return;
        }

        taskRepository.markAllTasksDeleted(selectedChildId);
        for (Map<String, Object> task : payloadService.asMapList(payload.get("tasks"))) {
            Long taskId = payloadService.asLong(task.get("id"));
            String name = payloadService.asString(task.get("name"));
            if (taskId == null || name == null || name.isBlank()) {
                continue;
            }

            taskRepository.upsertTask(new TaskUpsertCommand(
                familyDbId,
                selectedChildId,
                taskId,
                new TaskContentCommand(
                    name,
                    payloadService.defaultInt(task.get("coins"), 0),
                    payloadService.firstNonBlank(
                        payloadService.asString(task.get("groupName")),
                        payloadService.asString(task.get("group"))
                    ),
                    payloadService.asString(task.get("comment")),
                    payloadService.firstNonBlank(
                        payloadService.asString(task.get("cueWhen")),
                        payloadService.asString(task.get("cue_when"))
                    ),
                    payloadService.firstNonBlank(
                        payloadService.asString(task.get("cueAction")),
                        payloadService.asString(task.get("cue_action"))
                    ),
                    payloadService.firstNonBlank(
                        payloadService.asString(task.get("icon")),
                        payloadService.asString(task.get("graphic"))
                    )
                ),
                payloadService.serializeFrequency(task.get("frequency")),
                payloadService.coalesceInt(task.get("moneyLimit"), task.get("money_limit")),
                payloadService.defaultBoolean(
                    payloadService.coalesceFirst(task.get("isActive"), task.get("is_active")),
                    true
                ),
                payloadService.defaultBoolean(task.get("isDeleted"), false)
            ));
        }
    }

    void syncShopItems(int familyDbId, Integer selectedChildId, Map<String, Object> payload) {
        if (selectedChildId == null || !payload.containsKey("shop")) {
            return;
        }

        shopItemRepository.markAllShopItemsDeleted(selectedChildId);
        for (Map<String, Object> item : payloadService.asMapList(payload.get("shop"))) {
            Long itemId = payloadService.asLong(item.get("id"));
            String name = payloadService.asString(item.get("name"));
            if (itemId == null || name == null || name.isBlank()) {
                continue;
            }

            shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(
                familyDbId,
                selectedChildId,
                itemId,
                name,
                payloadService.defaultInt(item.get("price"), 0),
                payloadService.firstNonBlank(
                    payloadService.asString(item.get("groupName")),
                    payloadService.asString(item.get("group"))
                ),
                payloadService.serializeFrequency(item.get("frequency")),
                payloadService.asString(item.get("comment")),
                payloadService.coalesceInt(item.get("moneyLimit"), item.get("money_limit")),
                payloadService.defaultBoolean(
                    payloadService.coalesceFirst(item.get("isActive"), item.get("is_active")),
                    true
                ),
                payloadService.defaultBoolean(item.get("isDeleted"), false),
                payloadService.firstNonBlank(
                    payloadService.asString(item.get("icon")),
                    payloadService.asString(item.get("graphic"))
                )
            ));
        }
        clearInvalidRewardGoal(familyDbId, selectedChildId);
    }

    private void clearInvalidRewardGoal(int familyDbId, int childId) {
        childRepository.findByIdOptional(childId)
            .map(ChildEntity::getRewardGoalItemId)
            .filter(Objects::nonNull)
            .filter(itemId -> !shopItemRepository.isActiveItem(familyDbId, childId, itemId))
            .ifPresent(itemId -> childRepository.updateRewardGoal(childId, null));
    }
}
