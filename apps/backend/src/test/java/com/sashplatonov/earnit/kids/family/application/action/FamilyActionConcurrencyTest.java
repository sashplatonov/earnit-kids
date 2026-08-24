package com.sashplatonov.earnit.kids.family.application.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskContentCommand;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class FamilyActionConcurrencyTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject FamilyActionService familyActionService;
    @Inject FamilyRepository familyRepository;
    @Inject ChildRepository childRepository;
    @Inject TaskRepository taskRepository;
    @Inject ShopItemRepository shopItemRepository;
    @Inject HistoryRepository historyRepository;
    @Inject PurchaseRequestRepository purchaseRequestRepository;
    @Inject EntityManager entityManager;

    @Test
    void crossFamilyIdentifiersAreNotAcceptedByTheCanonicalActionBoundary() {
        Fixture fixture = createFixture("isolation", 20);

        assertThat(familyActionService.completeTask(fixture.familyId(), fixture.otherChild().getId(), fixture.otherTaskId()))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(familyActionService.requestTaskCompletion(
            fixture.familyId(), fixture.otherChild().getId(), fixture.otherTaskId(), "cross-family"))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(familyActionService.purchaseItem(fixture.familyId(), fixture.otherChild().getId(), fixture.otherItemId()))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(familyActionService.requestItemPurchase(
            fixture.familyId(), fixture.otherChild().getId(), fixture.otherItemId(), "cross-family"))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(familyActionService.approveRequest(fixture.familyId(), null, fixture.otherRequestId()))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(familyActionService.deleteHistoryEntry(
            fixture.familyId(), fixture.otherChild().getId(), fixture.otherHistoryExternalId()))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(familyActionService.adjustBalance(fixture.familyId(), fixture.otherChild().getId(), 5, "cross-family"))
            .isInstanceOf(OperationResult.Failure.class);

        assertThat(childRepository.findById(fixture.otherChild().getId()).getBalance()).isEqualTo(20);
        assertThat(purchaseRequestRepository.findById(fixture.otherRequestId()).getStatus())
            .isEqualTo(PurchaseRequestStatus.pending);
        assertThat(historyRepository.count("familyId = ?1 AND childId = ?2", fixture.otherFamilyDbId(), fixture.otherChild().getId()))
            .isEqualTo(1);
    }

    @Test
    void parallelApprovalResolvesOnePendingRequestAndWritesOneLedgerEntry() throws Exception {
        Fixture fixture = createFixture("approval", 20);

        List<OperationResult<?>> results = runInParallel(
            () -> familyActionService.approveRequest(fixture.familyId(), null, fixture.requestId()),
            () -> familyActionService.approveRequest(fixture.familyId(), null, fixture.requestId()));

        assertThat(results.stream().filter(OperationResult::isSuccess)).hasSize(1);
        assertThat(results.stream().filter(OperationResult::isFailure)).hasSize(1);
        entityManager.clear();
        assertThat(purchaseRequestRepository.find("id = ?1", fixture.requestId()).firstResult().getStatus())
            .isEqualTo(PurchaseRequestStatus.approved);
        assertThat(childRepository.findById(fixture.child().getId()).getBalance()).isEqualTo(10);
        assertThat(historyRepository.count("familyId = ?1 AND childId = ?2", fixture.familyDbId(), fixture.child().getId()))
            .isEqualTo(1);
    }

    @Test
    void parallelPurchaseCannotOverdrawChildOrDuplicateLedgerEntry() throws Exception {
        Fixture fixture = createFixture("purchase", 10);

        List<OperationResult<?>> results = runInParallel(
            () -> familyActionService.purchaseItem(fixture.familyId(), fixture.child().getId(), fixture.itemId()),
            () -> familyActionService.purchaseItem(fixture.familyId(), fixture.child().getId(), fixture.itemId()));

        assertThat(results.stream().filter(OperationResult::isSuccess)).hasSize(1);
        assertThat(results.stream().filter(OperationResult::isFailure)).hasSize(1);
        assertThat(childRepository.findById(fixture.child().getId()).getBalance()).isZero();
        assertThat(historyRepository.count("familyId = ?1 AND childId = ?2", fixture.familyDbId(), fixture.child().getId()))
            .isEqualTo(1);
    }

    private List<OperationResult<?>> runInParallel(Callable<? extends OperationResult<?>> first,
                                                    Callable<? extends OperationResult<?>> second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<? extends OperationResult<?>> firstResult = executor.submit(first);
            Future<? extends OperationResult<?>> secondResult = executor.submit(second);
            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private Fixture createFixture(String suffix, int balance) {
        String familyId = "port005-" + suffix + "-" + System.nanoTime();
        String otherFamilyId = familyId + "-other";
        int familyDbId = familyRepository.create(familyId, familyId + "@test.local", "secret").orElseThrow().getId();
        int otherFamilyDbId = familyRepository.create(otherFamilyId, otherFamilyId + "@test.local", "secret").orElseThrow().getId();
        ChildEntity child = childRepository.createChild(familyDbId, "Primary").orElseThrow();
        ChildEntity otherChild = childRepository.createChild(otherFamilyDbId, "Other").orElseThrow();
        childRepository.updateBalance(child.getId(), balance);
        childRepository.updateBalance(otherChild.getId(), 20);

        long taskId = 51001L;
        long otherTaskId = 51002L;
        taskRepository.upsertTask(new TaskUpsertCommand(familyDbId, child.getId(), taskId,
            new TaskContentCommand("Task", 10, "School", "", "", ""),
            OBJECT_MAPPER.createObjectNode(), 0, true, false));
        taskRepository.upsertTask(new TaskUpsertCommand(otherFamilyDbId, otherChild.getId(), otherTaskId,
            new TaskContentCommand("Other task", 10, "School", "", "", ""),
            OBJECT_MAPPER.createObjectNode(), 0, true, false));

        long itemId = 52001L;
        long otherItemId = 52002L;
        shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(familyDbId, child.getId(), itemId,
            "Item", 10, "Fun", OBJECT_MAPPER.createObjectNode(), "", 0, true, false));
        shopItemRepository.upsertShopItem(new ShopItemUpsertCommand(otherFamilyDbId, otherChild.getId(), otherItemId,
            "Other item", 10, "Fun", OBJECT_MAPPER.createObjectNode(), "", 0, true, false));

        purchaseRequestRepository.createRequest(familyDbId, child.getId(), 53001L, null, "Item", itemId,
            10, PurchaseRequestType.shop_purchase, 0);
        purchaseRequestRepository.createRequest(otherFamilyDbId, otherChild.getId(), 53002L, null, "Other item", otherItemId,
            10, PurchaseRequestType.shop_purchase, 0);
        long requestId = purchaseRequestRepository.list("familyId = ?1 AND childId = ?2", familyDbId, child.getId())
            .getFirst().getId();
        long otherRequestId = purchaseRequestRepository.list("familyId = ?1 AND childId = ?2", otherFamilyDbId, otherChild.getId())
            .getFirst().getId();
        historyRepository.addHistory(otherFamilyDbId, otherChild.getId(), 54001L, HistoryEntryType.earn,
            20, "Other history", 0, null, null, null);
        return new Fixture(familyId, familyDbId, otherFamilyDbId, child, otherChild, itemId, otherItemId,
            requestId, otherRequestId, otherTaskId, 54001L);
    }

    private record Fixture(String familyId, int familyDbId, int otherFamilyDbId, ChildEntity child,
                           ChildEntity otherChild, long itemId, long otherItemId, long requestId,
                           long otherRequestId, long otherTaskId, long otherHistoryExternalId) { }
}
