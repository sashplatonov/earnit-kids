package com.sashplatonov.earnit.kids.service.family.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sashplatonov.earnit.kids.dto.request.BulkActionType;
import com.sashplatonov.earnit.kids.dto.request.FrequencyPeriod;
import com.sashplatonov.earnit.kids.dto.request.ShopItemImportType;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.command.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.command.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.observability.BackendKpiMetrics;
@ExtendWith(MockitoExtension.class)
class FamilyActionServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-17T09:30:00Z");

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;
    @Mock TaskRepository taskRepository;
    @Mock ShopItemRepository shopItemRepository;
    @Mock HistoryRepository historyRepository;
    @Mock PurchaseRequestRepository purchaseRequestRepository;
    @Mock FamilyService familyService;

    private FamilyActionServiceImpl service;
    private BackendKpiMetrics backendKpiMetrics;

    @BeforeEach
    void setUp() {
        RequestLocaleHolder.set("en");
        backendKpiMetrics = new BackendKpiMetrics(new SimpleMeterRegistry());
        service = new FamilyActionServiceImpl(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            familyService,
            TestConfigFactory.timeProvider(FIXED_NOW),
            new FrequencyWindowService(),
            backendKpiMetrics
        );
    }

    @Test
    void purchaseItem_insufficientBalance_returnsFailure() {
        ChildEntity child = child(10, 1, "Alice", 3);
        ShopItemEntity item = shopItem(10, 1, 2001L, "Console", 7);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOf(item);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            1,
            10,
            2001L
        )).thenReturn(itemQuery);

        OperationResult<FamilyDataResponse> result = service.purchaseItem("fam-1", 10, 2001L);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(child.getBalance()).isEqualTo(3);
        verify(historyRepository, never()).persist(org.mockito.ArgumentMatchers.<HistoryEntryEntity>any());
        verify(familyService, never()).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void requestTaskCompletion_persistsPendingEarnRequest() {
        ChildEntity child = child(10, 1, "Alice", 0);
        TaskEntity task = task(10, 1, 3001L, "Убрать комнату", 50);
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOf(task);
        FamilyDataResponse payload = emptyPayload(false, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false AND active = true",
            1,
            10,
            3001L
        )).thenReturn(taskQuery);
        when(familyService.loadFamilyData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.requestTaskCompletion("fam-1", 10, 3001L, null);

        assertThat(successValue(result)).isEqualTo(payload);

        ArgumentCaptor<PurchaseRequestEntity> captor = ArgumentCaptor.forClass(PurchaseRequestEntity.class);
        verify(purchaseRequestRepository).persist(captor.capture());
        assertThat(captor.getValue().getRequestType()).isEqualTo(PurchaseRequestType.earn);
        assertThat(captor.getValue().getTaskId()).isEqualTo(3001L);
        assertThat(captor.getValue().getTaskName()).isEqualTo("Убрать комнату");
        assertThat(captor.getValue().getCoins()).isEqualTo(50);
        verify(familyService).loadFamilyData("fam-1", 10, false);
    }

    @Test
    void setRewardGoal_activeOwnedItem_persistsAndReturnsChildSnapshot() {
        ChildEntity child = child(10, 1, "Alice", 0);
        ShopItemEntity item = shopItem(10, 1, 2001L, "Console", 7);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOf(item);
        FamilyDataResponse payload = emptyPayload(false, 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            1, 10, 2001L
        )).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.setRewardGoal("fam-1", 10, 2001L);

        assertThat(successValue(result)).isEqualTo(payload);
        verify(childRepository).updateRewardGoal(10, 2001L);
    }

    @Test
    void setRewardGoal_itemOutsideChildScope_returnsFailureWithoutMutation() {
        ChildEntity child = child(10, 1, "Alice", 0);
        io.quarkus.hibernate.orm.panache.PanacheQuery emptyItemQuery = queryOf();
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            1, 10, 999L
        )).thenReturn(emptyItemQuery);

        assertThat(service.setRewardGoal("fam-1", 10, 999L)).isInstanceOf(OperationResult.Failure.class);
        verify(childRepository, never()).updateRewardGoal(10, 999L);
    }

    @Test
    void requestTaskCompletion_whenDailyLimitReached_returnsFailure() {
        ChildEntity child = child(10, 1, "Alice", 0);
        TaskEntity task = task(10, 1, 3001L, "Убрать комнату", 50);
        task.setFrequency(frequency(1, "day"));
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOf(task);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false AND active = true",
            1,
            10,
            3001L
        )).thenReturn(taskQuery);
        when(purchaseRequestRepository.countPendingTaskRequestsInWindow(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.any(Instant.class)
        ))
            .thenReturn(1L);
        when(historyRepository.countTaskEarnsInWindow(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.any(Instant.class)
        ))
            .thenReturn(0L);

        OperationResult<FamilyDataResponse> result = service.requestTaskCompletion("fam-1", 10, 3001L, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<FamilyDataResponse> failure = (OperationResult.Failure<FamilyDataResponse>) result;
        assertThat(failure.errorCode()).isEqualTo("TASK_REQUEST_LIMIT_REACHED");
        assertThat(failure.message()).contains("this task").contains("00:00");
        verify(purchaseRequestRepository, never()).persist(org.mockito.ArgumentMatchers.<PurchaseRequestEntity>any());
        verify(familyService, never()).loadFamilyData("fam-1", 10, false);
    }

    @Test
    void requestItemPurchase_persistsPendingShopRequest() {
        ChildEntity child = child(10, 1, "Alice", 50);
        ShopItemEntity item = shopItem(10, 1, 2001L, "PlayStation", 50);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOf(item);
        FamilyDataResponse payload = emptyPayload(false, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            1,
            10,
            2001L
        )).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.requestItemPurchase("fam-1", 10, 2001L, null);

        assertThat(successValue(result)).isEqualTo(payload);

        ArgumentCaptor<PurchaseRequestEntity> captor = ArgumentCaptor.forClass(PurchaseRequestEntity.class);
        verify(purchaseRequestRepository).persist(captor.capture());
        assertThat(captor.getValue().getRequestType()).isEqualTo(PurchaseRequestType.shop_purchase);
        assertThat(captor.getValue().getItemId()).isEqualTo(2001L);
        assertThat(captor.getValue().getTaskName()).isEqualTo("PlayStation");
        assertThat(captor.getValue().getCoins()).isEqualTo(50);
        verify(familyService).loadFamilyData("fam-1", 10, false);
    }

    @Test
    void requestItemPurchase_whenDailyLimitReached_returnsFailure() {
        ChildEntity child = child(10, 1, "Alice", 50);
        ShopItemEntity item = shopItem(10, 1, 2001L, "PlayStation", 50);
        item.setFrequency(frequency(1, "day"));
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOf(item);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            1,
            10,
            2001L
        )).thenReturn(itemQuery);
        when(purchaseRequestRepository.countPendingItemRequestsInWindow(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.any(Instant.class)
        ))
            .thenReturn(0L);
        when(historyRepository.countShopPurchasesInWindow(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.any(Instant.class)
        ))
            .thenReturn(1L);

        OperationResult<FamilyDataResponse> result = service.requestItemPurchase("fam-1", 10, 2001L, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<FamilyDataResponse> failure = (OperationResult.Failure<FamilyDataResponse>) result;
        assertThat(failure.errorCode()).isEqualTo("ITEM_REQUEST_LIMIT_REACHED");
        assertThat(failure.message()).contains("this item").contains("00:00");
        verify(purchaseRequestRepository, never()).persist(org.mockito.ArgumentMatchers.<PurchaseRequestEntity>any());
        verify(familyService, never()).loadFamilyData("fam-1", 10, false);
    }

    @Test
    void requestTaskCompletion_whenLocaleIsRussian_returnsRussianLimitMessage() {
        RequestLocaleHolder.set("ru");

        ChildEntity child = child(10, 1, "Alice", 0);
        TaskEntity task = task(10, 1, 3001L, "Убрать комнату", 50);
        task.setFrequency(frequency(1, "day"));
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOf(task);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false AND active = true",
            1,
            10,
            3001L
        )).thenReturn(taskQuery);
        when(purchaseRequestRepository.countPendingTaskRequestsInWindow(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.any(Instant.class)
        ))
            .thenReturn(1L);
        when(historyRepository.countTaskEarnsInWindow(
            org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.any(Instant.class)
        ))
            .thenReturn(0L);

        OperationResult<FamilyDataResponse> result = service.requestTaskCompletion("fam-1", 10, 3001L, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<FamilyDataResponse> failure = (OperationResult.Failure<FamilyDataResponse>) result;
        assertThat(failure.message()).contains("этому заданию").contains("00:00");
    }

    @Test
    void approveRequest_purchase_updatesBalanceStatusAndHistory() {
        ChildEntity child = child(10, 1, "Alice", 20);
        Instant requestCreatedAt = Instant.parse("2026-04-15T07:20:00Z");
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4001L)
            .familyId(1)
            .childId(10)
            .itemId(2001L)
            .taskName("Console")
            .coins(7)
            .requestType(PurchaseRequestType.shop_purchase)
            .status(PurchaseRequestStatus.pending)
            .moneyAmount(250)
            .createdAt(requestCreatedAt)
            .build();
        ShopItemEntity item = shopItem(10, 1, 2001L, "Console", 7);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOf(item);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(purchaseRequestRepository.findByIdOptional(4001L)).thenReturn(Optional.of(request));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            1,
            10,
            2001L
        )).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.approveRequest("fam-1", 10, 4001L);

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(child.getBalance()).isEqualTo(13);
        assertThat(request.getStatus()).isEqualTo(PurchaseRequestStatus.approved);

        ArgumentCaptor<HistoryEntryEntity> captor = ArgumentCaptor.forClass(HistoryEntryEntity.class);
        verify(historyRepository).persist(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(HistoryEntryType.spend);
        assertThat(captor.getValue().getAmount()).isEqualTo(7);
        assertThat(captor.getValue().getDescription()).isEqualTo("Console");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(requestCreatedAt);
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void approveRequest_task_updatesBalanceStatusAndHistory() {
        ChildEntity child = child(10, 1, "Alice", 0);
        Instant requestCreatedAt = Instant.parse("2026-04-14T18:05:00Z");
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4002L)
            .familyId(1)
            .childId(10)
            .taskId(3001L)
            .taskName("Убрать комнату")
            .coins(50)
            .requestType(PurchaseRequestType.earn)
            .status(PurchaseRequestStatus.pending)
            .moneyAmount(0)
            .createdAt(requestCreatedAt)
            .build();
        TaskEntity task = task(10, 1, 3001L, "Убрать комнату", 50);
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOf(task);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(purchaseRequestRepository.findByIdOptional(4002L)).thenReturn(Optional.of(request));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false AND active = true",
            1,
            10,
            3001L
        )).thenReturn(taskQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.approveRequest("fam-1", 10, 4002L);

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(child.getBalance()).isEqualTo(50);
        assertThat(request.getStatus()).isEqualTo(PurchaseRequestStatus.approved);

        ArgumentCaptor<HistoryEntryEntity> captor = ArgumentCaptor.forClass(HistoryEntryEntity.class);
        verify(historyRepository).persist(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(HistoryEntryType.earn);
        assertThat(captor.getValue().getAmount()).isEqualTo(50);
        assertThat(captor.getValue().getDescription()).isEqualTo("Убрать комнату");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(requestCreatedAt);
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void deleteHistoryEntry_reversesBalanceAndDeletesHistory() {
        ChildEntity child = child(10, 1, "Alice", 20);
        HistoryEntryEntity historyEntry = HistoryEntryEntity.builder()
            .id(1L)
            .familyId(1)
            .childId(10)
            .externalId(5001L)
            .type(HistoryEntryType.earn)
            .amount(5)
            .description("Read")
            .createdAt(FIXED_NOW)
            .build();
        io.quarkus.hibernate.orm.panache.PanacheQuery historyQuery = queryOf(historyEntry);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(historyRepository.find(
            "familyId = ?1 AND childId = ?2 AND externalId = ?3",
            1,
            10,
            5001L
        )).thenReturn(historyQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.deleteHistoryEntry("fam-1", 10, 5001L);

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(child.getBalance()).isEqualTo(15);
        verify(historyRepository).delete(historyEntry);
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void bulkTaskAction_delete_marksSelectedTasksDeleted() {
        ChildEntity child = child(10, 1, "Alice", 20);
        TaskEntity first = task(10, 1, 3001L, "Убрать комнату", 50);
        TaskEntity second = task(10, 1, 3002L, "Помыть посуду", 30);
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOfList(first, second);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find("familyId = ?1 AND childId = ?2", 1, 10)).thenReturn(taskQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.bulkTaskAction(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest(10, BulkActionType.delete, List.of(3001L, 3002L), null)
        );

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(first.isDeleted()).isTrue();
        assertThat(second.isDeleted()).isTrue();
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void bulkTaskAction_changeGroup_updatesSelectedTasksGroup() {
        ChildEntity child = child(10, 1, "Alice", 20);
        TaskEntity first = task(10, 1, 3001L, "Убрать комнату", 50);
        TaskEntity second = task(10, 1, 3002L, "Помыть посуду", 30);
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOfList(first, second);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find("familyId = ?1 AND childId = ?2", 1, 10)).thenReturn(taskQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.bulkTaskAction(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest(10, BulkActionType.change_group, List.of(3001L, 3002L), "Home stuff")
        );

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(first.getGroupName()).isEqualTo("Home stuff");
        assertThat(second.getGroupName()).isEqualTo("Home stuff");
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void bulkTaskAction_unknownAction_returnsFailureWithoutMutatingEntities() {
        ChildEntity child = child(10, 1, "Alice", 20);
        TaskEntity first = task(10, 1, 3001L, "Убрать комнату", 50);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));

        OperationResult<FamilyDataResponse> result = service.bulkTaskAction(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest(10, null, List.of(3001L), null)
        );

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(first.isDeleted()).isFalse();
        assertThat(first.isActive()).isTrue();
        verify(familyService, never()).loadFamilyData(anyString(), anyInt(), anyBoolean());
    }

    @Test
    void bulkShopItemAction_block_marksSelectedItemsInactive() {
        ChildEntity child = child(10, 1, "Alice", 20);
        child.setRewardGoalItemId(2001L);
        ShopItemEntity first = shopItem(10, 1, 2001L, "Console", 7);
        ShopItemEntity second = shopItem(10, 1, 2002L, "Game", 5);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOfList(first, second);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find("familyId = ?1 AND childId = ?2", 1, 10)).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.bulkShopItemAction(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.BulkShopItemActionRequest(10, BulkActionType.block, List.of(2001L, 2002L), null)
        );

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        verify(childRepository).updateRewardGoal(10, null);
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void bulkShopItemAction_changeGroup_updatesSelectedItemsGroup() {
        ChildEntity child = child(10, 1, "Alice", 20);
        ShopItemEntity first = shopItem(10, 1, 2001L, "Console", 7);
        ShopItemEntity second = shopItem(10, 1, 2002L, "Game", 5);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOfList(first, second);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find("familyId = ?1 AND childId = ?2", 1, 10)).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.bulkShopItemAction(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.BulkShopItemActionRequest(10, BulkActionType.change_group, List.of(2001L, 2002L), "Big rewards")
        );

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(first.getGroupName()).isEqualTo("Big rewards");
        assertThat(second.getGroupName()).isEqualTo("Big rewards");
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void importTasks_persistsRowsAndReturnsRefreshedPayload() {
        ChildEntity child = child(10, 1, "Alice", 20);
        FamilyDataResponse payload = emptyPayload(true, 10);
        io.quarkus.hibernate.orm.panache.PanacheQuery emptyTaskQuery = queryOfList();

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find("familyId = ?1 AND childId = ?2", 1, 10)).thenReturn(emptyTaskQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        FamilyDataResponse result = service.importTasks(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.ImportTasksRequest(
                10,
                List.of(
                    new com.sashplatonov.earnit.kids.dto.request.ImportTaskRowRequest(1, "Clean desk", 10, "Home", null, 2, FrequencyPeriod.day, null, true),
                    new com.sashplatonov.earnit.kids.dto.request.ImportTaskRowRequest(2, "Read book", 5, null, "Before bed", null, null, 15, false)
                )
            )
        );

        assertThat(result).isEqualTo(payload);
        verify(taskRepository).upsertTask(argThat(command ->
            command.familyDbId() == 1
                && command.childId() == 10
                && command.taskId() == 1L
                && "Clean desk".equals(command.content().name())
                && command.content().coins() == 10
                && "Home".equals(command.content().groupName())
                && command.frequency() != null
                && command.content().comment() == null
                && command.moneyLimit() == null
                && command.active()
                && !command.deleted()
        ));
        verify(taskRepository).upsertTask(argThat(command ->
            command.familyDbId() == 1
                && command.childId() == 10
                && command.taskId() == 2L
                && "Read book".equals(command.content().name())
                && command.content().coins() == 5
                && command.content().groupName() == null
                && command.frequency() == null
                && "Before bed".equals(command.content().comment())
                && command.moneyLimit() == 15
                && !command.active()
                && !command.deleted()
        ));
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void importShopItems_persistsSeasonFrequencyAndReturnsRefreshedPayload() {
        ChildEntity child = child(10, 1, "Alice", 20);
        FamilyDataResponse payload = emptyPayload(true, 10);
        io.quarkus.hibernate.orm.panache.PanacheQuery emptyShopQuery = queryOfList();

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find("familyId = ?1 AND childId = ?2", 1, 10)).thenReturn(emptyShopQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        FamilyDataResponse result = service.importShopItems(
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.ImportShopItemsRequest(
                10,
                List.of(
                    new com.sashplatonov.earnit.kids.dto.request.ImportShopItemRowRequest(
                        1, "Summer goal", 150, "Big rewards", "Once per summer", 1, FrequencyPeriod.season, null, null, true
                    )
                )
            )
        );

        assertThat(result).isEqualTo(payload);
        verify(shopItemRepository).upsertShopItem(argThat(command ->
            command.familyDbId() == 1
                && command.childId() == 10
                && command.itemId() == 1L
                && "Summer goal".equals(command.name())
                && command.price() == 150
                && "Big rewards".equals(command.groupName())
                && command.frequency() != null
                && "season".equals(command.frequency().get("period").asText())
                && command.frequency().get("limit").asInt() == 1
                && "Once per summer".equals(command.comment())
                && command.moneyLimit() == null
                && command.active()
                && !command.deleted()
        ));
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void importShopItems_rejectsInvalidRowsWithStructuredErrors() {
        ChildEntity child = child(10, 1, "Alice", 20);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));

        try {
            service.importShopItems(
                "fam-1",
                new com.sashplatonov.earnit.kids.dto.request.ImportShopItemsRequest(
                    10,
                    List.of(
                        new com.sashplatonov.earnit.kids.dto.request.ImportShopItemRowRequest(1, "Tablet time", null, null, null, 0, null, -1, null, true)
                    )
                )
            );
        } catch (com.sashplatonov.earnit.kids.exception.ImportValidationException exception) {
            assertThat(exception.response().errors()).anySatisfy(error -> {
                assertThat(error.row()).isEqualTo(1);
                assertThat(error.field()).isEqualTo("price");
            });
            assertThat(exception.response().errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("frequencyLimit");
            });
            assertThat(exception.response().errors()).anySatisfy(error -> {
                assertThat(error.field()).isEqualTo("moneyLimit");
            });
            return;
        }

        throw new AssertionError("Expected ImportValidationException");
    }

    @Test
    void importTasks_rejectsEmptyRowsWithStructuredErrors() {
        ChildEntity child = child(10, 1, "Alice", 20);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));

        try {
            service.importTasks(
                "fam-1",
                new com.sashplatonov.earnit.kids.dto.request.ImportTasksRequest(10, List.of())
            );
        } catch (com.sashplatonov.earnit.kids.exception.ImportValidationException exception) {
            assertThat(exception.response().errors()).anySatisfy(error -> {
                assertThat(error.row()).isEqualTo(0);
                assertThat(error.field()).isEqualTo("rows");
            });
            verifyNoInteractions(taskRepository, shopItemRepository, historyRepository, purchaseRequestRepository);
            return;
        }

        throw new AssertionError("Expected ImportValidationException");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static io.quarkus.hibernate.orm.panache.PanacheQuery queryOf(Object... entities) {
        io.quarkus.hibernate.orm.panache.PanacheQuery query = org.mockito.Mockito.mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        when(query.firstResultOptional()).thenReturn(Optional.ofNullable(entities.length > 0 ? entities[0] : null));
        return query;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static io.quarkus.hibernate.orm.panache.PanacheQuery queryOfList(Object... entities) {
        io.quarkus.hibernate.orm.panache.PanacheQuery query = org.mockito.Mockito.mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        when(query.list()).thenReturn(List.of(entities));
        return query;
    }

    private static FamilyDataResponse emptyPayload(boolean isAdmin, Integer childId) {
        return new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(), List.of(), isAdmin, List.of(), childId, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T successValue(OperationResult<T> result) {
        assertThat(result).isInstanceOf(OperationResult.Success.class);
        return ((OperationResult.Success<T>) result).value();
    }

    private static ChildEntity child(int childId, int familyDbId, String name, int balance) {
        return ChildEntity.builder()
            .id(childId)
            .familyDbId(familyDbId)
            .name(name)
            .balance(balance)
            .build();
    }

    private static ShopItemEntity shopItem(int childId, int familyId, long itemId, String name, int price) {
        return ShopItemEntity.builder()
            .familyId(familyId)
            .childId(childId)
            .itemId(itemId)
            .name(name)
            .price(price)
            .groupName("Fun")
            .comment("Reward")
            .moneyLimit(250)
            .build();
    }

    private static TaskEntity task(int childId, int familyId, long taskId, String name, int coins) {
        return TaskEntity.builder()
            .familyId(familyId)
            .childId(childId)
            .taskId(taskId)
            .name(name)
            .coins(coins)
            .groupName("Home")
            .comment("Task")
            .build();
    }

    private static JsonNode frequency(int limit, String period) {
        return JsonNodeFactory.instance.objectNode()
            .put("limit", limit)
            .put("period", period);
    }
}
