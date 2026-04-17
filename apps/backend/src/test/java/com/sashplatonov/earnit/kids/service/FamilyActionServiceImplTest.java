package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
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
        return new FamilyDataResponse(0, List.of(), List.of(), List.of(), List.of(), List.of(), isAdmin, List.of(), childId, null, null, null);
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
}