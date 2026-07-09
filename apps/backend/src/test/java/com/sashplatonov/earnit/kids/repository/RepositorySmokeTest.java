package com.sashplatonov.earnit.kids.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RepositorySmokeTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject FamilyRepository familyRepository;
    @Inject ChildRepository childRepository;
    @Inject TaskRepository taskRepository;
    @Inject ShopItemRepository shopItemRepository;
    @Inject HistoryRepository historyRepository;
    @Inject PurchaseRequestRepository purchaseRequestRepository;
    @Inject FriendRepository friendRepository;
    @Inject EntityManager entityManager;

    @Test
    @Transactional
    void repositoriesSupportBasicLifecycle() throws Exception {
        String familyId = "fam_repo_test";
        String email = "repo@test.com";
        Optional<FamilyEntity> createdFamily = familyRepository.create(familyId, email, "secret123", false, "verify-token");
        assertThat(createdFamily).isPresent();

        FamilyEntity family = createdFamily.get();
        int familyDbId = family.getId();

        assertThat(family.getCreatedAt()).isNotNull();
        assertThat(family.getUpdatedAt()).isNotNull();
        assertThat(family.getLastActivity()).isNotNull();

        assertThat(familyRepository.findById(familyId)).isPresent();
        assertThat(familyRepository.findByEmail(email)).isPresent();
        assertThat(familyRepository.getDbId(familyId)).contains(familyDbId);
        assertThat(familyRepository.findByDbId(familyDbId)).isPresent();

        assertThat(familyRepository.updatePassword(familyId, "new-secret")).isTrue();
        assertThat(familyRepository.updateLastActivity(familyId)).isTrue();
        assertThat(familyRepository.updateLastSelectedChild(familyId, null)).isTrue();
        assertThat(familyRepository.setResetToken(familyId, "reset-token", Instant.now().plusSeconds(3600))).isTrue();
        assertThat(familyRepository.findByResetToken("reset-token")).isPresent();
        assertThat(familyRepository.clearResetToken(familyId)).isTrue();
        assertThat(familyRepository.findByResetToken("reset-token")).isEmpty();
        assertThat(familyRepository.verifyFamily(familyId)).isTrue();
        assertThat(familyRepository.findByVerificationToken("verify-token")).isEmpty();
        assertThat(familyRepository.setBlocked(familyId, true)).isTrue();
        assertThat(familyRepository.setBlocked(familyId, false)).isTrue();

        Optional<ChildEntity> child1Opt = childRepository.createChild(familyDbId, "Kid One");
        Optional<ChildEntity> child2Opt = childRepository.createChild(familyDbId, "Kid Two");
        assertThat(child1Opt).isPresent();
        assertThat(child2Opt).isPresent();

        ChildEntity child1 = child1Opt.get();
        ChildEntity child2 = child2Opt.get();

        assertThat(child1.getToken()).hasSize(16);
        assertThat(child1.getCreatedAt()).isNotNull();
        assertThat(child1.getUpdatedAt()).isNotNull();
        assertThat(child2.getToken()).hasSize(16);
        assertThat(child2.getCreatedAt()).isNotNull();
        assertThat(child2.getUpdatedAt()).isNotNull();

        assertThat(childRepository.getChildren(familyDbId)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(childRepository.findByToken(child1.getToken())).isPresent();
        assertThat(childRepository.updateBalance(child1.getId(), 123)).isTrue();
        assertThat(childRepository.updateName(child1.getId(), "Kid One Updated")).isTrue();
        assertThat(childRepository.updateSettings(child1.getId(), "Kid One Updated", 5, 500)).isTrue();
        assertThat(childRepository.updateTheme(child1.getId(), ChildTheme.ocean)).isTrue();
        assertThat(childRepository.regenerateToken(child1.getId())).isPresent();

        assertThat(childRepository.isNicknameTaken(familyDbId, "Kid One Updated", child1.getId())).isFalse();
        assertThat(childRepository.isNicknameTaken(familyDbId, "Kid Two", null)).isTrue();
        assertThat(childRepository.searchByNickname("Kid", child1.getId())).isNotEmpty();

        long taskExternalId = 70001L;
        long itemExternalId = 80001L;

        assertThat(taskRepository.upsertTask(new TaskUpsertCommand(
            familyDbId, child1.getId(), taskExternalId, "Read", 5, "Study",
            OBJECT_MAPPER.readTree("{\"period\":\"day\"}"), "comment", 100, false, false))).isTrue();
        assertThat(taskRepository.upsertTask(new TaskUpsertCommand(
            familyDbId, child1.getId(), taskExternalId, "Read updated", 6, "Study",
            OBJECT_MAPPER.readTree("{\"period\":\"week\",\"limit\":2}"), "comment2", 150, true, false))).isTrue();
        assertThat(shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(
            familyDbId, child1.getId(), itemExternalId, "Toy", 7, "Fun",
            OBJECT_MAPPER.readTree("{\"period\":\"week\"}"), "comment", 50, false, false))).isTrue();
        assertThat(shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(
            familyDbId, child1.getId(), itemExternalId, "Toy updated", 8, "Fun",
            OBJECT_MAPPER.readTree("{\"period\":\"month\",\"limit\":1}"), "comment2", 55, true, false))).isTrue();

        assertThat(taskRepository.getTasks(child1.getId())).isNotEmpty();
        assertThat(shopItemRepository.getShopItems(child1.getId())).isNotEmpty();

        TaskEntity storedTask = taskRepository.getTasks(child1.getId()).stream()
            .filter(taskEntity -> taskEntity.getTaskId() == taskExternalId)
            .findFirst()
            .orElseThrow();
        assertThat(storedTask.isActive()).isTrue();
        assertThat(storedTask.isDeleted()).isFalse();

        ShopItemEntity storedShopItem = shopItemRepository.getShopItems(child1.getId()).stream()
            .filter(shopItemEntity -> shopItemEntity.getItemId() == itemExternalId)
            .findFirst()
            .orElseThrow();
        assertThat(storedShopItem.isActive()).isTrue();
        assertThat(storedShopItem.isDeleted()).isFalse();

        assertThat(historyRepository.addHistory(familyDbId, child1.getId(), 90001L, HistoryEntryType.earn,
            5, "Read", 0, taskExternalId, "Study", "Great")).isTrue();
        assertThat(historyRepository.addHistory(familyDbId, child1.getId(), 90002L, HistoryEntryType.spend,
            3, "Toy", 300, itemExternalId, "Fun", "Bought")).isTrue();

        assertThat(historyRepository.getHistory(child1.getId(), 10, 0)).isNotEmpty();
        assertThat(historyRepository.getHistoryCount(child1.getId())).isGreaterThanOrEqualTo(2);

        assertThat(purchaseRequestRepository.createRequest(familyDbId, child1.getId(), 91001L,
            taskExternalId, "Read", itemExternalId, 5, PurchaseRequestType.shop, 300)).isTrue();
        assertThat(purchaseRequestRepository.getRequests(familyDbId, 10, 0)).isNotEmpty();
        assertThat(purchaseRequestRepository.getRequestsCount(familyDbId)).isGreaterThanOrEqualTo(1);

        int requestId = purchaseRequestRepository.getRequests(familyDbId, 1, 0).getFirst().getId().intValue();
        assertThat(purchaseRequestRepository.updateRequestStatus(requestId, PurchaseRequestStatus.approved)).isTrue();

        Instant paginationTimestamp = Instant.parse("2026-04-22T12:00:00Z");
        historyRepository.replaceHistory(familyDbId, child1.getId(), java.util.List.of(
            HistoryEntryEntity.builder()
                .familyId(familyDbId)
                .childId(child1.getId())
                .externalId(92001L)
                .type(HistoryEntryType.earn)
                .amount(2)
                .description("First")
                .createdAt(paginationTimestamp)
                .build(),
            HistoryEntryEntity.builder()
                .familyId(familyDbId)
                .childId(child1.getId())
                .externalId(92002L)
                .type(HistoryEntryType.earn)
                .amount(3)
                .description("Second")
                .createdAt(paginationTimestamp)
                .build()
        ));
        purchaseRequestRepository.replaceRequests(familyDbId, java.util.List.of(
            PurchaseRequestEntity.builder()
                .familyId(familyDbId)
                .childId(child1.getId())
                .externalId(93001L)
                .taskId(taskExternalId)
                .taskName("Read")
                .coins(5)
                .requestType(PurchaseRequestType.earn)
                .createdAt(paginationTimestamp)
                .build(),
            PurchaseRequestEntity.builder()
                .familyId(familyDbId)
                .childId(child1.getId())
                .externalId(93002L)
                .itemId(itemExternalId)
                .taskName("Toy")
                .coins(7)
                .requestType(PurchaseRequestType.shop_purchase)
                .createdAt(paginationTimestamp)
                .build()
        ));
        entityManager.flush();
        entityManager.clear();

        assertThat(historyRepository.getHistory(child1.getId(), 1, 0))
            .extracting(HistoryEntryEntity::getExternalId)
            .containsExactly(92002L);
        assertThat(historyRepository.getHistory(child1.getId(), 1, 1))
            .extracting(HistoryEntryEntity::getExternalId)
            .containsExactly(92001L);
        assertThat(historyRepository.getHistory(child1.getId(), 1, 2)).isEmpty();

        assertThat(purchaseRequestRepository.getRequests(familyDbId, 1, 0))
            .extracting(PurchaseRequestEntity::getExternalId)
            .containsExactly(93002L);
        assertThat(purchaseRequestRepository.getRequests(familyDbId, 1, 1))
            .extracting(PurchaseRequestEntity::getExternalId)
            .containsExactly(93001L);
        assertThat(purchaseRequestRepository.getRequests(familyDbId, 1, 2)).isEmpty();

        assertThat(indexNamesForTables("HISTORY", "REQUESTS")).contains(
            "IDX_HISTORY_FAMILY_CHILD_TYPE_RELATED_CREATED",
            "IDX_REQUESTS_FAMILY_CHILD_TASK_STATUS_CREATED",
            "IDX_REQUESTS_FAMILY_CHILD_ITEM_STATUS_CREATED"
        );
        assertThat(explainPlan(String.format(
            "SELECT id FROM EARNIT_KIDS.history WHERE family_id = %d AND child_id = %d AND type = 'earn' " +
                "AND related_id = %d AND created_at >= TIMESTAMP '2026-04-22 00:00:00' " +
                "AND created_at < TIMESTAMP '2026-04-23 00:00:00'",
            familyDbId, child1.getId(), taskExternalId
        ))).contains("IDX_HISTORY_FAMILY_CHILD_TYPE_RELATED_CREATED");
        assertThat(explainPlan(String.format(
            "SELECT id FROM EARNIT_KIDS.requests WHERE family_id = %d AND child_id = %d AND task_id = %d " +
                "AND status = 'pending' AND created_at >= TIMESTAMP '2026-04-22 00:00:00' " +
                "AND created_at < TIMESTAMP '2026-04-23 00:00:00'",
            familyDbId, child1.getId(), taskExternalId
        ))).contains("IDX_REQUESTS_FAMILY_CHILD_TASK_STATUS_CREATED");
        assertThat(explainPlan(String.format(
            "SELECT id FROM EARNIT_KIDS.requests WHERE family_id = %d AND child_id = %d AND item_id = %d " +
                "AND status = 'pending' AND created_at >= TIMESTAMP '2026-04-22 00:00:00' " +
                "AND created_at < TIMESTAMP '2026-04-23 00:00:00'",
            familyDbId, child1.getId(), itemExternalId
        ))).contains("IDX_REQUESTS_FAMILY_CHILD_ITEM_STATUS_CREATED");

        assertThat(friendRepository.addFriend(child1.getId(), child2.getId())).isTrue();
        assertThat(friendRepository.getFriendChildIds(child1.getId())).contains(child2.getId());
        assertThat(friendRepository.getFriendChildIds(child2.getId())).contains(child1.getId());

        assertThat(taskRepository.softDeleteTask(child1.getId(), taskExternalId)).isTrue();
        assertThat(shopItemRepository.softDeleteShopItem(child1.getId(), itemExternalId)).isTrue();

        // Touch generic repository classes to ensure they are covered too.
        TaskEntity task = taskRepository.listAll().stream().findFirst().orElse(null);
        ShopItemEntity item = shopItemRepository.listAll().stream().findFirst().orElse(null);
        HistoryEntryEntity history = historyRepository.listAll().stream().findFirst().orElse(null);
        PurchaseRequestEntity request = purchaseRequestRepository.listAll().stream().findFirst().orElse(null);
        FriendEntity friend = friendRepository.listAll().stream().findFirst().orElse(null);

        assertThat(task).isNotNull();
        assertThat(item).isNotNull();
        assertThat(history).isNotNull();
        assertThat(request).isNotNull();
        assertThat(friend).isNotNull();
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isNotNull();
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isNotNull();
        assertThat(history.getCreatedAt()).isNotNull();
        assertThat(history.getUpdatedAt()).isNotNull();
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getUpdatedAt()).isNotNull();
        assertThat(friend.getCreatedAt()).isNotNull();
        assertThat(friend.getUpdatedAt()).isNotNull();

        assertThat(childRepository.deleteChild(child2.getId())).isTrue();
        assertThat(childRepository.deleteChild(child2.getId())).isFalse();

        assertThat(familyRepository.updatePassword("missing-family", "x")).isFalse();
        assertThat(familyRepository.updateLastActivity("missing-family")).isFalse();
        assertThat(familyRepository.updateLastSelectedChild("missing-family", 1)).isFalse();
        assertThat(familyRepository.verifyFamily("missing-family")).isFalse();
        assertThat(familyRepository.setResetToken("missing-family", "x", Instant.now())).isFalse();
        assertThat(familyRepository.clearResetToken("missing-family")).isFalse();
        assertThat(familyRepository.setBlocked("missing-family", true)).isFalse();

        assertThat(childRepository.updateBalance(999999, 1)).isFalse();
        assertThat(childRepository.updateName(999999, "x")).isFalse();
        assertThat(childRepository.updateSettings(999999, "x", 1, 1)).isFalse();
        assertThat(childRepository.updateTheme(999999, ChildTheme.ocean)).isFalse();
        assertThat(childRepository.regenerateToken(999999)).isEmpty();
    }

    private java.util.List<String> indexNamesForTables(String firstTable, String secondTable) {
        java.util.List<?> rows = entityManager.createNativeQuery(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES " +
                    "WHERE UPPER(TABLE_SCHEMA) = 'EARNIT_KIDS' " +
                    "AND UPPER(TABLE_NAME) IN (?1, ?2) ORDER BY INDEX_NAME")
            .setParameter(1, firstTable)
            .setParameter(2, secondTable)
            .getResultList();
        return rows.stream().map(String::valueOf).toList();
    }

    private String explainPlan(String sql) {
        java.util.List<?> rows = entityManager.createNativeQuery("EXPLAIN " + sql).getResultList();
        return rows.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"));
    }
}
