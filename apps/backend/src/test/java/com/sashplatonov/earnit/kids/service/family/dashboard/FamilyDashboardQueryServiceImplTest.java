package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.FriendRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sashplatonov.earnit.kids.service.observability.BackendKpiMetrics;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyDashboardQueryServiceImplTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;
    @Mock HistoryRepository historyRepository;
    @Mock PurchaseRequestRepository purchaseRequestRepository;
    @Mock FriendRepository friendRepository;
    @Mock TaskRepository taskRepository;
    @Mock ShopItemRepository shopItemRepository;

    private SimpleMeterRegistry meterRegistry;
    private BackendKpiMetrics backendKpiMetrics;
    private FamilyDashboardQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        backendKpiMetrics = new BackendKpiMetrics(meterRegistry);
        FamilyDashboardMapper mapper = FamilyDashboardMapper.INSTANCE;
        FamilyDashboardScopeLoader scopeLoader = new FamilyDashboardScopeLoader(familyRepository, childRepository);
        FamilyDashboardCatalogLoader catalogLoader = new FamilyDashboardCatalogLoader(
            historyRepository,
            taskRepository,
            shopItemRepository,
            mapper,
            OBJECT_MAPPER
        );
        FamilyDashboardHydrator hydrator = new FamilyDashboardHydrator(
            historyRepository,
            purchaseRequestRepository,
            friendRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            mapper,
            OBJECT_MAPPER
        );
        FamilyDashboardResponseAssembler responseAssembler = new FamilyDashboardResponseAssembler(
            hydrator,
            mapper,
            OBJECT_MAPPER
        );
        service = new FamilyDashboardQueryServiceImpl(
            scopeLoader,
            catalogLoader,
            responseAssembler,
            backendKpiMetrics
        );
    }

    @Test
    void loadFamilyData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("missing", null, true);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void loadFamilyData_existingChildData_mapsTasksHistoryRequestsAndFriends() {
        ChildEntity child1 = child(10, 1, "Alice", 100);
        ChildEntity child2 = child(11, 1, "Bob", 50);
        child1.setTaskGroupOrder("[\"Дом\",\"Учеба\"]");
        child1.setShopGroupOrder("[\"Призы\",\"Выходные\"]");
        child1.setChildTaskGroupOrder("[\"Учеба\",\"Дом\"]");
        child1.setChildShopGroupOrder("[\"Выходные\",\"Призы\"]");
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.of("Bedtime by 20:30"));
        when(familyRepository.getLastSelectedChildId("fam-1")).thenReturn(Optional.of(11));
        when(childRepository.getChildren(1)).thenReturn(List.of(child1, child2));

        TaskEntity task = TaskEntity.builder().taskId(1001L).childId(10).name("Read").coins(5)
            .frequency(readJson("{\"limit\":1,\"period\":\"day\"}"))
            .groupName("Reading")
            .comment("Pages").build();
        ShopItemEntity item = ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(7)
            .frequency(readJson("{\"limit\":2,\"period\":\"week\"}"))
            .groupName("Fun")
            .moneyLimit(250)
            .comment("Prize").build();
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .familyId(1)
            .childId(10)
            .externalId(3001L)
            .type(HistoryEntryType.earn)
            .amount(5)
            .relatedId(1001L)
            .createdAt(FIXED_NOW)
            .build();
        HistoryEntryEntity purchaseHistory = HistoryEntryEntity.builder()
            .familyId(1)
            .childId(10)
            .externalId(3002L)
            .type(HistoryEntryType.spend)
            .amount(7)
            .relatedId(2001L)
            .createdAt(FIXED_NOW.minus(Duration.ofDays(1)))
            .build();
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4001L)
            .childId(10)
            .familyId(1)
            .taskId(2001L)
            .taskName("Toy")
            .itemId(2001L)
            .coins(7)
            .requestType(PurchaseRequestType.shop_purchase)
            .moneyAmount(250)
            .build();

        when(taskRepository.getTasks(10)).thenReturn(List.of(task));
        when(shopItemRepository.getShopItems(10)).thenReturn(List.of(item));
        when(historyRepository.getHistory(10, 50, 0)).thenReturn(List.of(history));
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.earn))
            .thenReturn(java.util.Map.of(1001L, FIXED_NOW));
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.spend))
            .thenReturn(java.util.Map.of(2001L, FIXED_NOW.minus(Duration.ofDays(1))));
        when(purchaseRequestRepository.getRequests(1, 50, 0)).thenReturn(List.of(request));
        when(friendRepository.getFriendChildIds(10)).thenReturn(List.of(11));
        when(childRepository.findByChildIds(List.of(11))).thenReturn(List.of(child2));

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("fam-1", 10, true);

        FamilyDataResponse payload = successValue(result);
        assertThat(payload.balance()).isEqualTo(100);
        assertThat(payload.tasks()).hasSize(1);
        assertThat(payload.shop()).hasSize(1);
        assertThat(payload.history()).hasSize(1);
        assertThat(payload.requests()).hasSize(1);
        assertThat(payload.friends()).hasSize(1);
        assertThat(payload.children()).hasSize(2);
        assertThat(payload.rules()).isEqualTo("Bedtime by 20:30");
        assertThat(payload.lastSelectedChildId()).isEqualTo(11);
        assertThat(payload.history().getFirst().title()).isEqualTo("Read");
        assertThat(payload.requests().getFirst().title()).isEqualTo("Toy");
        assertThat(payload.tasks().getFirst().lastCompletedAt()).isEqualTo(FIXED_NOW.toString());
        assertThat(payload.shop().getFirst().lastPurchasedAt()).isEqualTo(FIXED_NOW.minus(Duration.ofDays(1)).toString());
    }

    @Test
    void loadFamilyShellData_existingChildData_omitsHeavyCollections() {
        ChildEntity child1 = child(10, 1, "Alice", 100);
        ChildEntity child2 = child(11, 1, "Bob", 50);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.of("Bedtime by 20:30"));
        when(familyRepository.getLastSelectedChildId("fam-1")).thenReturn(Optional.of(11));
        when(childRepository.getChildren(1)).thenReturn(List.of(child1, child2));
        when(taskRepository.getTasks(10)).thenReturn(List.of());
        when(shopItemRepository.getShopItems(10)).thenReturn(List.of());
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.earn))
            .thenReturn(java.util.Map.of());
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.spend))
            .thenReturn(java.util.Map.of());

        OperationResult<FamilyDashboardShellResponse> result = service.loadFamilyShellData("fam-1", 10, true);

        FamilyDashboardShellResponse payload = successValue(result);
        assertThat(payload.balance()).isEqualTo(100);
        assertThat(payload.tasks()).isEmpty();
        assertThat(payload.shop()).isEmpty();
        assertThat(payload.activeChildId()).isEqualTo(10);
        assertThat(payload.lastSelectedChildId()).isEqualTo(11);
        assertThat(payload.children()).hasSize(2);
    }

    @Test
    void loadFamilyDetailData_existingChildData_returnsHeavyCollectionsOnly() {
        ChildEntity child1 = child(10, 1, "Alice", 100);
        ChildEntity child2 = child(11, 1, "Bob", 50);
        TaskEntity task = TaskEntity.builder().taskId(1001L).childId(10).name("Read").coins(5)
            .frequency(readJson("{\"limit\":1,\"period\":\"day\"}"))
            .groupName("Reading")
            .comment("Pages").build();
        ShopItemEntity item = ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(7)
            .frequency(readJson("{\"limit\":2,\"period\":\"week\"}"))
            .groupName("Fun")
            .moneyLimit(250)
            .comment("Prize").build();
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .familyId(1)
            .childId(10)
            .externalId(3001L)
            .type(HistoryEntryType.earn)
            .amount(5)
            .relatedId(1001L)
            .createdAt(FIXED_NOW)
            .build();
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4001L)
            .childId(10)
            .familyId(1)
            .taskId(2001L)
            .taskName("Toy")
            .itemId(2001L)
            .coins(7)
            .requestType(PurchaseRequestType.shop_purchase)
            .moneyAmount(250)
            .build();

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.of("Bedtime by 20:30"));
        when(familyRepository.getLastSelectedChildId("fam-1")).thenReturn(Optional.of(11));
        when(childRepository.getChildren(1)).thenReturn(List.of(child1, child2));
        when(taskRepository.getTasks(10)).thenReturn(List.of(task));
        when(shopItemRepository.getShopItems(10)).thenReturn(List.of(item));
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.earn))
            .thenReturn(java.util.Map.of(1001L, FIXED_NOW));
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.spend))
            .thenReturn(java.util.Map.of(2001L, FIXED_NOW.minus(Duration.ofDays(1))));
        when(historyRepository.getHistory(10, 50, 0)).thenReturn(List.of(history));
        when(purchaseRequestRepository.getRequests(1, 50, 0)).thenReturn(List.of(request));
        when(friendRepository.getFriendChildIds(10)).thenReturn(List.of(11));
        when(childRepository.findByChildIds(List.of(11))).thenReturn(List.of(child2));

        OperationResult<FamilyDashboardDetailResponse> result = service.loadFamilyDetailData("fam-1", 10, true);

        FamilyDashboardDetailResponse payload = successValue(result);
        assertThat(payload.history()).hasSize(1);
        assertThat(payload.requests()).hasSize(1);
        assertThat(payload.friends()).hasSize(1);
        assertThat(payload.history().getFirst().title()).isEqualTo("Read");
        assertThat(payload.requests().getFirst().title()).isEqualTo("Toy");
    }

    @Test
    void loadFamilyData_childSession_limitsVisibleChildrenAndRequests() {
        ChildEntity child1 = child(10, 1, "Alice", 100);
        ChildEntity child2 = child(11, 1, "Bob", 50);
        PurchaseRequestEntity ownRequest = PurchaseRequestEntity.builder()
            .id(4001L)
            .childId(10)
            .familyId(1)
            .coins(7)
            .requestType(PurchaseRequestType.shop)
            .build();
        PurchaseRequestEntity siblingRequest = PurchaseRequestEntity.builder()
            .id(4002L)
            .childId(11)
            .familyId(1)
            .coins(9)
            .requestType(PurchaseRequestType.earn)
            .build();

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.of("Ask before spending"));
        when(familyRepository.getLastSelectedChildId("fam-1")).thenReturn(Optional.of(11));
        when(childRepository.getChildren(1)).thenReturn(List.of(child1, child2));
        when(taskRepository.getTasks(10)).thenReturn(List.of());
        when(shopItemRepository.getShopItems(10)).thenReturn(List.of());
        when(historyRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.earn))
            .thenReturn(java.util.Map.of());
        when(historyRepository.loadLatestTimestampsByRelatedId(10, HistoryEntryType.spend))
            .thenReturn(java.util.Map.of());
        when(purchaseRequestRepository.getRequests(1, 50, 0)).thenReturn(List.of(ownRequest, siblingRequest));
        when(friendRepository.getFriendChildIds(10)).thenReturn(List.of());

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("fam-1", 10, false);

        FamilyDataResponse payload = successValue(result);
        assertThat(payload.isAdmin()).isNull();
        assertThat(payload.rules()).isEqualTo("Ask before spending");
        assertThat(payload.children()).singleElement().satisfies(child -> assertThat(child.id()).isEqualTo(10));
        assertThat(payload.lastSelectedChildId()).isEqualTo(10);
        assertThat(payload.requests()).singleElement().satisfies(request -> assertThat(request.childId()).isEqualTo(10));
    }

    private static JsonNode readJson(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ChildEntity child(int id, int familyDbId, String name, int balance) {
        return ChildEntity.builder()
            .id(id)
            .familyDbId(familyDbId)
            .name(name)
            .token("token-" + id)
            .balance(balance)
            .monthlyLimit(10000)
            .dailyCoinLimit(50)
            .theme("ocean")
            .build();
    }

    private static <T> T successValue(OperationResult<T> result) {
        assertThat(result).isInstanceOf(OperationResult.Success.class);
        return ((OperationResult.Success<T>) result).value();
    }
}
