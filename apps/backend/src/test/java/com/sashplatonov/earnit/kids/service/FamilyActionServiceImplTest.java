package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() {
        service = new FamilyActionServiceImpl(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            familyService,
            TestConfigFactory.timeProvider(FIXED_NOW)
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
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false",
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
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false",
            1,
            10,
            3001L
        )).thenReturn(taskQuery);
        when(familyService.loadFamilyData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.requestTaskCompletion("fam-1", 10, 3001L);

        assertThat(successValue(result)).isEqualTo(payload);

        ArgumentCaptor<PurchaseRequestEntity> captor = ArgumentCaptor.forClass(PurchaseRequestEntity.class);
        verify(purchaseRequestRepository).persist(captor.capture());
        assertThat(captor.getValue().getRequestType()).isEqualTo("earn");
        assertThat(captor.getValue().getTaskId()).isEqualTo(3001L);
        assertThat(captor.getValue().getTaskName()).isEqualTo("Убрать комнату");
        assertThat(captor.getValue().getCoins()).isEqualTo(50);
        verify(familyService).loadFamilyData("fam-1", 10, false);
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
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false",
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

        OperationResult<FamilyDataResponse> result = service.requestTaskCompletion("fam-1", 10, 3001L);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<FamilyDataResponse> failure = (OperationResult.Failure<FamilyDataResponse>) result;
        assertThat(failure.errorCode()).isEqualTo("TASK_REQUEST_LIMIT_REACHED");
        assertThat(failure.message()).contains("этому заданию").contains("00:00");
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
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false",
            1,
            10,
            2001L
        )).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.requestItemPurchase("fam-1", 10, 2001L);

        assertThat(successValue(result)).isEqualTo(payload);

        ArgumentCaptor<PurchaseRequestEntity> captor = ArgumentCaptor.forClass(PurchaseRequestEntity.class);
        verify(purchaseRequestRepository).persist(captor.capture());
        assertThat(captor.getValue().getRequestType()).isEqualTo("shop_purchase");
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
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false",
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

        OperationResult<FamilyDataResponse> result = service.requestItemPurchase("fam-1", 10, 2001L);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<FamilyDataResponse> failure = (OperationResult.Failure<FamilyDataResponse>) result;
        assertThat(failure.errorCode()).isEqualTo("ITEM_REQUEST_LIMIT_REACHED");
        assertThat(failure.message()).contains("этому товару").contains("00:00");
        verify(purchaseRequestRepository, never()).persist(org.mockito.ArgumentMatchers.<PurchaseRequestEntity>any());
        verify(familyService, never()).loadFamilyData("fam-1", 10, false);
    }

    @Test
    void approveRequest_purchase_updatesBalanceStatusAndHistory() {
        ChildEntity child = child(10, 1, "Alice", 20);
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4001L)
            .familyId(1)
            .childId(10)
            .itemId(2001L)
            .taskName("Console")
            .coins(7)
            .requestType("shop_purchase")
            .status("pending")
            .moneyAmount(250)
            .build();
        ShopItemEntity item = shopItem(10, 1, 2001L, "Console", 7);
        io.quarkus.hibernate.orm.panache.PanacheQuery itemQuery = queryOf(item);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(purchaseRequestRepository.findByIdOptional(4001L)).thenReturn(Optional.of(request));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false",
            1,
            10,
            2001L
        )).thenReturn(itemQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.approveRequest("fam-1", 10, 4001L);

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(child.getBalance()).isEqualTo(13);
        assertThat(request.getStatus()).isEqualTo("approved");

        ArgumentCaptor<HistoryEntryEntity> captor = ArgumentCaptor.forClass(HistoryEntryEntity.class);
        verify(historyRepository).persist(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("spend");
        assertThat(captor.getValue().getAmount()).isEqualTo(7);
        assertThat(captor.getValue().getDescription()).isEqualTo("Console");
        verify(familyService).loadFamilyData("fam-1", 10, true);
    }

    @Test
    void approveRequest_task_updatesBalanceStatusAndHistory() {
        ChildEntity child = child(10, 1, "Alice", 0);
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4002L)
            .familyId(1)
            .childId(10)
            .taskId(3001L)
            .taskName("Убрать комнату")
            .coins(50)
            .requestType("earn")
            .status("pending")
            .moneyAmount(0)
            .build();
        TaskEntity task = task(10, 1, 3001L, "Убрать комнату", 50);
        io.quarkus.hibernate.orm.panache.PanacheQuery taskQuery = queryOf(task);
        FamilyDataResponse payload = emptyPayload(true, 10);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(purchaseRequestRepository.findByIdOptional(4002L)).thenReturn(Optional.of(request));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child));
        when(taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false",
            1,
            10,
            3001L
        )).thenReturn(taskQuery);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        OperationResult<FamilyDataResponse> result = service.approveRequest("fam-1", 10, 4002L);

        assertThat(successValue(result)).isEqualTo(payload);
        assertThat(child.getBalance()).isEqualTo(50);
        assertThat(request.getStatus()).isEqualTo("approved");

        ArgumentCaptor<HistoryEntryEntity> captor = ArgumentCaptor.forClass(HistoryEntryEntity.class);
        verify(historyRepository).persist(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("earn");
        assertThat(captor.getValue().getAmount()).isEqualTo(50);
        assertThat(captor.getValue().getDescription()).isEqualTo("Убрать комнату");
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
            .type("earn")
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static io.quarkus.hibernate.orm.panache.PanacheQuery queryOf(Object entity) {
        io.quarkus.hibernate.orm.panache.PanacheQuery query = org.mockito.Mockito.mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        when(query.firstResultOptional()).thenReturn(Optional.ofNullable(entity));
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