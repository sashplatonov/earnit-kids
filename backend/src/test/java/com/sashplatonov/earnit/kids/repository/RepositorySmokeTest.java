package com.sashplatonov.earnit.kids.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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
    @Inject FamilyDataRepository familyDataRepository;
    @Inject TaskRepository taskRepository;
    @Inject ShopItemRepository shopItemRepository;
    @Inject HistoryRepository historyRepository;
    @Inject PurchaseRequestRepository purchaseRequestRepository;
    @Inject FriendRepository friendRepository;

    @Test
    @Transactional
    void repositoriesSupportBasicLifecycle() throws Exception {
        String familyId = "fam_repo_test";
        String email = "repo@test.com";
        Optional<FamilyEntity> createdFamily = familyRepository.create(familyId, email, "secret123", false, "verify-token");
        assertThat(createdFamily).isPresent();

        FamilyEntity family = createdFamily.get();
        int familyDbId = family.getId();

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

        assertThat(childRepository.getChildren(familyDbId)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(childRepository.findByToken(child1.getToken())).isPresent();
        assertThat(childRepository.updateBalance(child1.getId(), 123)).isTrue();
        assertThat(childRepository.updateName(child1.getId(), "Kid One Updated")).isTrue();
        assertThat(childRepository.updateSettings(child1.getId(), "Kid One Updated", 5, 500)).isTrue();
        assertThat(childRepository.updateTheme(child1.getId(), "ocean")).isTrue();
        assertThat(childRepository.regenerateToken(child1.getId())).isPresent();

        assertThat(childRepository.isNicknameTaken(familyDbId, "Kid One Updated", child1.getId())).isFalse();
        assertThat(childRepository.isNicknameTaken(familyDbId, "Kid Two", null)).isTrue();
        assertThat(childRepository.searchByNickname("Kid", child1.getId())).isNotEmpty();

        long taskExternalId = 70001L;
        long itemExternalId = 80001L;

        assertThat(familyDataRepository.upsertTask(familyDbId, child1.getId(), taskExternalId,
            "Read", 5, "Study", OBJECT_MAPPER.readTree("{\"period\":\"day\"}"), "comment", 100, false)).isTrue();
        assertThat(familyDataRepository.upsertTask(familyDbId, child1.getId(), taskExternalId,
            "Read updated", 6, "Study", OBJECT_MAPPER.readTree("{\"period\":\"week\",\"limit\":2}"), "comment2", 150, false)).isTrue();
        assertThat(familyDataRepository.upsertShopItem(familyDbId, child1.getId(), itemExternalId,
            "Toy", 7, "Fun", OBJECT_MAPPER.readTree("{\"period\":\"week\"}"), "comment", 50, false)).isTrue();
        assertThat(familyDataRepository.upsertShopItem(familyDbId, child1.getId(), itemExternalId,
            "Toy updated", 8, "Fun", OBJECT_MAPPER.readTree("{\"period\":\"month\",\"limit\":1}"), "comment2", 55, false)).isTrue();

        assertThat(familyDataRepository.getTasks(child1.getId())).isNotEmpty();
        assertThat(familyDataRepository.getShopItems(child1.getId())).isNotEmpty();

        assertThat(familyDataRepository.addHistory(familyDbId, child1.getId(), 90001L, "earn",
            5, "Read", 0, taskExternalId, "Study", "Great")).isTrue();
        assertThat(familyDataRepository.addHistory(familyDbId, child1.getId(), 90002L, "spend",
            3, "Toy", 300, itemExternalId, "Fun", "Bought")).isTrue();

        assertThat(familyDataRepository.getHistory(child1.getId(), 10, 0)).isNotEmpty();
        assertThat(familyDataRepository.getHistoryCount(child1.getId())).isGreaterThanOrEqualTo(2);

        assertThat(familyDataRepository.createRequest(familyDbId, child1.getId(), 91001L,
            taskExternalId, "Read", itemExternalId, 5, "shop", 300)).isTrue();
        assertThat(familyDataRepository.getRequests(familyDbId, 10, 0)).isNotEmpty();
        assertThat(familyDataRepository.getRequestsCount(familyDbId)).isGreaterThanOrEqualTo(1);

        int requestId = familyDataRepository.getRequests(familyDbId, 1, 0).getFirst().getId().intValue();
        assertThat(familyDataRepository.updateRequestStatus(requestId, "approved")).isTrue();

        assertThat(familyDataRepository.addFriend(child1.getId(), child2.getId())).isTrue();
        assertThat(familyDataRepository.getFriendChildIds(child1.getId())).contains(child2.getId());
        assertThat(familyDataRepository.getFriendChildIds(child2.getId())).contains(child1.getId());

        assertThat(familyDataRepository.softDeleteTask(child1.getId(), taskExternalId)).isTrue();
        assertThat(familyDataRepository.softDeleteShopItem(child1.getId(), itemExternalId)).isTrue();

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
        assertThat(childRepository.updateTheme(999999, "ocean")).isFalse();
        assertThat(childRepository.regenerateToken(999999)).isEmpty();
    }
}
