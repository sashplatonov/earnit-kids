package com.sashplatonov.earnit.kids.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DomainModelSanityTest {

    @Test
    void familyEntity_tokenLifecycle_mutatesAsExpected() {
        FamilyEntity family = FamilyEntity.builder()
            .familyId("fam-1")
            .email("a@test.com")
            .adminPassword("secret")
            .verificationToken("v-token")
            .verified(false)
            .build();

        family.verify();
        assertThat(family.isVerified()).isTrue();
        assertThat(family.getVerificationToken()).isNull();

        Instant expiresAt = Instant.now().plusSeconds(60);
        family.setResetToken("r-token", expiresAt);
        assertThat(family.getResetToken()).isEqualTo("r-token");
        assertThat(family.getResetTokenExpiresAt()).isEqualTo(expiresAt);

        family.clearResetToken();
        assertThat(family.getResetToken()).isNull();
        assertThat(family.getResetTokenExpiresAt()).isNull();
    }

    @Test
    void childEntity_builderAndSetters_applyExpectedDefaults() {
        ChildEntity child = ChildEntity.builder()
            .familyDbId(1)
            .name("Kid")
            .build();

        assertThat(child.getToken()).hasSize(16);
        assertThat(child.getBalance()).isZero();
        assertThat(child.getMonthlyLimit()).isEqualTo(10000);
        assertThat(child.getDailyCoinLimit()).isZero();
        assertThat(child.getTheme()).isEqualTo("ocean");

        child.setBalance(33);
        child.setDailyCoinLimit(7);
        child.setMonthlyLimit(100);
        child.setTheme("mint");

        assertThat(child.getBalance()).isEqualTo(33);
        assertThat(child.getDailyCoinLimit()).isEqualTo(7);
        assertThat(child.getMonthlyLimit()).isEqualTo(100);
        assertThat(child.getTheme()).isEqualTo("mint");
    }

    @Test
    void taskAndShopEntities_mutatedFields_areRetained() {
        TaskEntity task = TaskEntity.builder()
            .familyId(1)
            .childId(2)
            .taskId(3)
            .name("Task")
            .coins(4)
            .build();

        task.setDeleted(true);
        task.setComment("Comment");
        task.setGroupName("Group");
        task.setFrequency("{\"period\":\"day\"}");
        task.setMoneyLimit(123);

        assertThat(task.isDeleted()).isTrue();
        assertThat(task.getComment()).isEqualTo("Comment");
        assertThat(task.getGroupName()).isEqualTo("Group");
        assertThat(task.getFrequency()).contains("day");
        assertThat(task.getMoneyLimit()).isEqualTo(123);

        ShopItemEntity item = ShopItemEntity.builder()
            .familyId(1)
            .childId(2)
            .itemId(3)
            .name("Item")
            .price(9)
            .build();

        item.setDeleted(true);
        item.setGroupName("G");
        item.setFrequency("{\"period\":\"week\"}");
        item.setComment("Prize");
        item.setMoneyLimit(200);
        item.setPrice(11);

        assertThat(item.isDeleted()).isTrue();
        assertThat(item.getPrice()).isEqualTo(11);
        assertThat(item.getGroupName()).isEqualTo("G");
        assertThat(item.getComment()).isEqualTo("Prize");
        assertThat(item.getMoneyLimit()).isEqualTo(200);
    }

    @Test
    void requestAndHistoryEntities_initializedValues_areExposed() {
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .familyId(1)
            .childId(2)
            .externalId(3L)
            .type("earn")
            .amount(5)
            .description("Done")
            .moneyAmount(7)
            .relatedId(8L)
            .groupName("A")
            .comment("C")
            .build();

        assertThat(history.getFamilyId()).isEqualTo(1);
        assertThat(history.getChildId()).isEqualTo(2);
        assertThat(history.getExternalId()).isEqualTo(3L);
        assertThat(history.getType()).isEqualTo("earn");
        assertThat(history.getAmount()).isEqualTo(5);
        assertThat(history.getDescription()).isEqualTo("Done");
        assertThat(history.getMoneyAmount()).isEqualTo(7);
        assertThat(history.getRelatedId()).isEqualTo(8L);
        assertThat(history.getGroupName()).isEqualTo("A");
        assertThat(history.getComment()).isEqualTo("C");

        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .familyId(1)
            .childId(2)
            .externalId(3L)
            .taskId(4L)
            .taskName("Task")
            .itemId(5L)
            .coins(6)
            .requestType("shop")
            .moneyAmount(7)
            .build();

        request.setStatus("approved");

        assertThat(request.getFamilyId()).isEqualTo(1);
        assertThat(request.getChildId()).isEqualTo(2);
        assertThat(request.getTaskId()).isEqualTo(4L);
        assertThat(request.getTaskName()).isEqualTo("Task");
        assertThat(request.getItemId()).isEqualTo(5L);
        assertThat(request.getCoins()).isEqualTo(6);
        assertThat(request.getRequestType()).isEqualTo("shop");
        assertThat(request.getMoneyAmount()).isEqualTo(7);
        assertThat(request.getStatus()).isEqualTo("approved");

        FriendEntity friend = FriendEntity.builder().childId(10).friendChildId(11).build();
        assertThat(friend.getChildId()).isEqualTo(10);
        assertThat(friend.getFriendChildId()).isEqualTo(11);
    }
}
