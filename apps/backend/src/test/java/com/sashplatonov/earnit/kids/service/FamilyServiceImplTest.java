package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyServiceImplTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;
    @Mock FamilyDataRepository familyDataRepository;
    @Mock HistoryRepository historyRepository;
    @Mock TaskRepository taskRepository;
    @Mock ShopItemRepository shopItemRepository;

    private FamilyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamilyServiceImpl(
            familyRepository,
            childRepository,
            familyDataRepository,
            historyRepository,
            taskRepository,
            shopItemRepository,
            TestConfigFactory.timeProvider(FIXED_NOW)
        );
    }

    @Test
    void loadFamilyData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("missing", null, true);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void loadFamilyData_familyWithoutChildren_returnsEmptyPayload() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of());

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("fam-1", null, true);

        FamilyDataResponse payload = successValue(result);
        assertThat(payload.balance()).isZero();
        assertThat(payload.children()).isEmpty();
        assertThat(payload.tasks()).isEmpty();
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

        when(familyDataRepository.getTasks(10)).thenReturn(List.of(task));
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of(item));
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of(history));
        when(historyRepository.list(
            "childId = ?1 AND type = ?2 AND relatedId IS NOT NULL ORDER BY createdAt DESC, id DESC",
            10,
            HistoryEntryType.earn
        )).thenReturn(List.of(history));
        when(historyRepository.list(
            "childId = ?1 AND type = ?2 AND relatedId IS NOT NULL ORDER BY createdAt DESC, id DESC",
            10,
            HistoryEntryType.spend
        )).thenReturn(List.of(purchaseHistory));
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of(request));
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of(11));
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
        assertThat(payload.history().getFirst().description()).isEqualTo("Read");
        assertThat(payload.history().getFirst().taskId()).isEqualTo(1001L);
        assertThat(payload.history().getFirst().taskName()).isEqualTo("Read");
        assertThat(payload.history().getFirst().groupName()).isEqualTo("Reading");
        assertThat(payload.history().getFirst().comment()).isEqualTo("Pages");
        assertThat(payload.requests().getFirst().title()).isEqualTo("Toy");
        assertThat(payload.requests().getFirst().description()).isEqualTo("Prize");
        assertThat(payload.requests().getFirst().groupName()).isEqualTo("Fun");
        assertThat(payload.requests().getFirst().itemName()).isEqualTo("Toy");
        assertThat(payload.requests().getFirst().itemComment()).isEqualTo("Prize");
        assertThat(payload.shop().getFirst().comment()).isEqualTo("Prize");
        assertThat(payload.tasks().getFirst().lastCompletedAt()).isEqualTo(FIXED_NOW.toString());
        assertThat(payload.shop().getFirst().lastPurchasedAt()).isEqualTo(FIXED_NOW.minus(Duration.ofDays(1)).toString());
        assertThat(payload.tasks().getFirst().frequency()).isInstanceOf(Map.class);
        assertThat(payload.shop().getFirst().frequency()).isInstanceOf(Map.class);
        assertThat(payload.children().getFirst().taskGroupOrder()).containsExactly("Дом", "Учеба");
        assertThat(payload.children().getFirst().shopGroupOrder()).containsExactly("Призы", "Выходные");
        assertThat(payload.children().getFirst().childTaskGroupOrder()).containsExactly("Учеба", "Дом");
        assertThat(payload.children().getFirst().childShopGroupOrder()).containsExactly("Выходные", "Призы");
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
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of(ownRequest, siblingRequest));
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

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

    @Test
    void createChild_invalidOrDuplicateName_returnsFailureOtherwiseSuccess() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.isNicknameTaken(1, "Alice", null)).thenReturn(true);

        assertThat(service.createChild("fam-1", " "))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.createChild("fam-1", "A".repeat(51)))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.createChild("fam-1", "Alice"))
            .isInstanceOf(OperationResult.Failure.class);

        ChildEntity created = child(20, 1, "Carla", 0);
        when(childRepository.isNicknameTaken(1, "Carla", null)).thenReturn(false);
        when(childRepository.createChild(1, "Carla")).thenReturn(Optional.of(created));

        assertThat(service.createChild("fam-1", "Carla"))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void deleteChild_foreignOrOwnedChild_returnsExpectedResult() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 2, "Other", 0)));

        assertThat(service.deleteChild("fam-1", 10))
            .isInstanceOf(OperationResult.Failure.class);

        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Own", 0)));
        assertThat(service.deleteChild("fam-1", 10))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void updateNickname_invalidOrDuplicateInput_returnsExpectedResult() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 0)));

        assertThat(service.updateNickname("fam-1", 10, " "))
            .isInstanceOf(OperationResult.Failure.class);

        when(childRepository.isNicknameTaken(1, "Alice", 10)).thenReturn(true);
        assertThat(service.updateNickname("fam-1", 10, "Alice"))
            .isInstanceOf(OperationResult.Failure.class);

        when(childRepository.isNicknameTaken(1, "Alice", 10)).thenReturn(false);
        assertThat(service.updateNickname("fam-1", 10, "Alice"))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void updateChildTheme_nullOrKnownTheme_returnsExpectedResult() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 0)));

        assertThat(service.updateChildTheme("fam-1", 10, null))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.updateChildTheme("fam-1", 10, ChildTheme.ocean))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void updateChildGroupOrder_nullOrKnownSection_returnsExpectedResult() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 0)));

        assertThat(service.updateChildGroupOrder("fam-1", 10, null, List.of("Дом"), false))
            .isInstanceOf(OperationResult.Failure.class);

        assertThat(service.updateChildGroupOrder("fam-1", 10, GroupOrderSection.tasks, List.of(" Дом ", "Учеба", "Дом"), false))
            .isInstanceOf(OperationResult.Success.class);

        verify(childRepository).updateGroupOrder(10, GroupOrderSection.tasks, false, "[\"Дом\",\"Учеба\"]");
    }

    @Test
    void updateChildGroupOrder_childSessionStoresPersonalOrderSeparately() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 0)));

        assertThat(service.updateChildGroupOrder("fam-1", 10, GroupOrderSection.shop, List.of("Хочу", "Потом"), true))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updateChildGroupOrder("fam-1", 10, GroupOrderSection.shop, List.of(), true))
            .isInstanceOf(OperationResult.Success.class);

        verify(childRepository).updateGroupOrder(10, GroupOrderSection.shop, true, "[\"Хочу\",\"Потом\"]");
        verify(childRepository).updateGroupOrder(10, GroupOrderSection.shop, true, null);
    }

    @Test
    void searchByNickname_shortQuery_returnsEmptyList() {
        OperationResult<List<FriendDto>> result = service.searchByNickname("ab", 10);
        assertThat(successValue(result)).isEmpty();
    }

    @Test
    void searchByNickname_matchingChildren_mapsFriendDtos() {
        when(childRepository.searchByNickname("alice", 10))
            .thenReturn(List.of(child(11, 2, "Alice 2", 17)));

        OperationResult<List<FriendDto>> result = service.searchByNickname("alice", 10);

        List<FriendDto> payload = successValue(result);
        assertThat(payload).singleElement().satisfies(friend -> {
            assertThat(friend.id()).isEqualTo(11);
            assertThat(friend.nickname()).isEqualTo("Alice 2");
            assertThat(friend.balance()).isEqualTo(17);
        });
    }

    @Test
    void addFriend_selfMissingOrValidFriend_returnsExpectedResult() {
        assertThat(service.addFriend("fam-1", 10, 10))
            .isInstanceOf(OperationResult.Failure.class);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(99)).thenReturn(Optional.empty());
        assertThat(service.addFriend("fam-1", 10, 99))
            .isInstanceOf(OperationResult.Failure.class);

        when(childRepository.findByIdOptional(11)).thenReturn(Optional.of(child(11, 2, "Friend", 0)));
        when(familyDataRepository.addFriend(10, 11)).thenReturn(true);
        assertThat(service.addFriend("fam-1", 10, 11))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void getFriendsData_existingFriends_mapsProfiles() {
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of(11, 12));
        when(childRepository.findByChildIds(List.of(11, 12))).thenReturn(List.of(child(11, 2, "A", 4)));

        OperationResult<List<FriendDto>> result = service.getFriendsData(10);

        List<FriendDto> payload = successValue(result);
        assertThat(payload).hasSize(1);
        assertThat(payload.get(0).nickname()).isEqualTo("A");
    }

    @Test
    void getAnalyticsData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        OperationResult<AnalyticsResponse> result = service.getAnalyticsData("missing", null, "month");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void getAnalyticsData_validHistory_buildsTypedAnalyticsResponse() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));

        Instant now = FIXED_NOW;
        List<HistoryEntryEntity> current = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.earn).amount(5).relatedId(1001L)
                .description("Task 1").createdAt(now.minusSeconds(3600)).build(),
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.spend).amount(3).relatedId(2001L)
                .description("Item 1").createdAt(now.minusSeconds(1800)).build()
        );
        List<HistoryEntryEntity> previous = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.earn).amount(2)
                .createdAt(now.minusSeconds(60 * 60 * 24 * 40L)).build()
        );
        List<HistoryEntryEntity> monthly = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type(HistoryEntryType.earn).amount(4)
                .relatedId(1001L).createdAt(now.minusSeconds(60 * 60 * 24)).build()
        );

        doReturn(current).doReturn(previous).doReturn(monthly)
            .when(historyRepository).list(anyString(), any(Object[].class));

        List<TaskEntity> tasks = List.of(
            TaskEntity.builder().familyId(1).childId(10).taskId(1001L).name("Read").coins(5).build(),
            TaskEntity.builder().familyId(1).childId(10).taskId(1002L).name("Clean").coins(7).build()
        );
        doReturn(tasks).doReturn(tasks)
            .when(taskRepository).list(anyString(), any(Object[].class));

        List<ShopItemEntity> items = List.of(
            ShopItemEntity.builder().familyId(1).childId(10).itemId(2001L).name("Toy").price(3).build()
        );
        doReturn(items).when(shopItemRepository).list(anyString(), any(Object[].class));

        OperationResult<AnalyticsResponse> result = service.getAnalyticsData("fam-1", null, "month");

        AnalyticsResponse payload = successValue(result);

        assertThat(payload.summary()).isNotNull();
        assertThat(payload.summary().totalEarned()).isGreaterThanOrEqualTo(0);
        assertThat(payload.summary().totalSpent()).isGreaterThanOrEqualTo(0);
        assertThat(payload.topTasks()).isNotNull();
        assertThat(payload.trends()).isNotNull();
        assertThat(payload.recommendations()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void getAnalyticsData_missingAggregateRows_fallsBackToZeroSummary() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(historyRepository.summarizePeriod(any(Integer.class), any(), any(), any())).thenReturn(null);
        doReturn(List.of()).when(taskRepository).list(anyString(), any(Object[].class));
        doReturn(List.of()).when(shopItemRepository).list(anyString(), any(Object[].class));

        OperationResult<AnalyticsResponse> result = service.getAnalyticsData("fam-1", 10, "month");

        AnalyticsResponse payload = successValue(result);
        assertThat(payload.summary().totalEarned()).isZero();
        assertThat(payload.summary().totalSpent()).isZero();
        assertThat(payload.comparison().totalEarned()).isZero();
        assertThat(payload.comparison().totalSpent()).isZero();
    }

    @Test
    void getHistory_existingEntries_returnsPaginatedResponse() {
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .familyId(1)
            .childId(10)
            .externalId(1L)
            .type(HistoryEntryType.earn)
            .amount(5)
            .relatedId(1001L)
            .createdAt(FIXED_NOW)
            .build();
        when(familyDataRepository.getHistory(10, 20, 0)).thenReturn(List.of(history));
        when(familyDataRepository.getHistoryCount(10)).thenReturn(1);
        when(familyDataRepository.getTasks(10)).thenReturn(List.of(
            TaskEntity.builder().taskId(1001L).childId(10).name("Read").coins(5).comment("Pages").build()
        ));
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of(
            ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(3).comment("Prize").build()
        ));

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 0)));

        OperationResult<PaginatedHistory> result = service.getHistory("fam-1", 10, 1, 20);

        PaginatedHistory payload = successValue(result);
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.total()).isEqualTo(1);
        assertThat(payload.items().getFirst().title()).isEqualTo("Read");
        assertThat(payload.items().getFirst().description()).isEqualTo("Read");
        assertThat(payload.items().getFirst().taskId()).isEqualTo(1001L);
        assertThat(payload.items().getFirst().taskName()).isEqualTo("Read");
        assertThat(payload.items().getFirst().comment()).isEqualTo("Pages");
    }

    @Test
    void getRequests_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());
        assertThat(service.getRequests("missing", 1, 20))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void getRequests_existingRows_returnsPaginatedData() {
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(10L)
            .familyId(1)
            .childId(10)
            .taskId(2001L)
            .taskName("Toy")
            .itemId(2001L)
            .coins(7)
            .requestType(PurchaseRequestType.shop_purchase)
            .moneyAmount(250)
            .build();

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyDataRepository.getRequests(1, 20, 0)).thenReturn(List.of(request));
        when(familyDataRepository.getRequestsCount(1)).thenReturn(1);
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of(
            ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(7)
                .groupName("Fun").comment("Prize").moneyLimit(250).build()
        ));

        OperationResult<PaginatedRequests> result = service.getRequests("fam-1", 1, 20);

        PaginatedRequests payload = successValue(result);
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.total()).isEqualTo(1);
        assertThat(payload.items().getFirst().title()).isEqualTo("Toy");
        assertThat(payload.items().getFirst().description()).isEqualTo("Prize");
        assertThat(payload.items().getFirst().groupName()).isEqualTo("Fun");
        assertThat(payload.items().getFirst().itemComment()).isEqualTo("Prize");
    }

    @Test
    void childTokenEndpoints_missingOrValidChild_returnExpectedResults() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.empty());
        assertThat(service.getChildLoginLink("fam-1", 10)).isInstanceOf(OperationResult.Failure.class);

        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 10)));
        assertThat(service.getChildLoginLink("fam-1", 10)).isInstanceOf(OperationResult.Success.class);

        when(childRepository.regenerateToken(10)).thenReturn(Optional.empty());
        assertThat(service.regenerateChildToken("fam-1", 10)).isInstanceOf(OperationResult.Failure.class);

        when(childRepository.regenerateToken(10)).thenReturn(Optional.of("new-token"));
        assertThat(service.regenerateChildToken("fam-1", 10)).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void familyScopedOperations_foreignChild_returnFailure() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(11)).thenReturn(Optional.of(child(11, 2, "Other", 0)));

        assertThat(service.updateNickname("fam-1", 11, "Alice"))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.updateChildSettings("fam-1", 11, "Alice", 5, 10))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.updateChildTheme("fam-1", 11, ChildTheme.ocean))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.getHistory("fam-1", 11, 1, 20))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.getChildLoginLink("fam-1", 11))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.regenerateChildToken("fam-1", 11))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void updatePreference_knownOrUnknownKey_returnsExpectedResult() {
        assertThat(service.updatePreference("fam-1", null, 1))
            .isInstanceOf(OperationResult.Failure.class);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(5)).thenReturn(Optional.of(child(5, 1, "Alice", 0)));

        assertThat(service.updatePreference("fam-1", FamilyPreferenceKey.lastSelectedChildId, 5))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updatePreference("fam-1", FamilyPreferenceKey.lastSelectedChildId, "5"))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updatePreference("fam-1", FamilyPreferenceKey.lastSelectedChildId, null))
            .isInstanceOf(OperationResult.Success.class);
        when(childRepository.findByIdOptional(12)).thenReturn(Optional.of(child(12, 2, "Other", 0)));
        assertThat(service.updatePreference("fam-1", FamilyPreferenceKey.lastSelectedChildId, 12))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.updatePreference("fam-1", FamilyPreferenceKey.lastSelectedChildId, "bad"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void saveFamilyData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        assertThat(service.saveFamilyData("missing", null, Map.of(), true))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void saveFamilyData_existingFamily_delegatesToLoadFamilyData() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        assertThat(service.saveFamilyData("fam-1", 10, Map.of(), true))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void saveFamilyData_childSession_ignoresSiblingPayloadAndDoesNotReplaceRequests() {
        ChildEntity child = child(10, 1, "Alice", 10);
        ChildEntity sibling = child(11, 1, "Bob", 15);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child, sibling));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("balance", 42);
        payload.put("children", List.of(
            Map.of("id", 10, "balance", 42),
            Map.of("id", 11, "balance", 9000)
        ));
        payload.put("requests", List.of(Map.of(
            "id", 401L,
            "childId", 11,
            "taskId", 101L,
            "taskName", "Read",
            "coins", 5,
            "status", "pending",
            "requestType", "earn"
        )));

        assertThat(service.saveFamilyData("fam-1", 10, payload, false))
            .isInstanceOf(OperationResult.Success.class);

        verify(familyRepository, never()).updateRules(anyString(), any());
        verify(childRepository).updateBalance(10, 42);
        verify(childRepository, never()).updateBalance(11, 9000);

        verify(familyDataRepository, never()).replaceRequests(eq(1), any());
    }

    @Test
    void saveFamilyData_existingFamily_persistsTasksShopButDoesNotReplaceLedgers() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        Instant timestamp = FIXED_NOW.minus(Duration.ofHours(1));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("balance", 42);
        payload.put("children", List.of(Map.of("id", 10, "balance", 42)));
        payload.put("tasks", List.of(Map.of(
            "id", 101L,
            "name", "Read",
            "coins", 5,
            "group", "Home",
            "frequency", Map.of("limit", 1, "period", "day"),
            "comment", "Daily",
            "money_limit", 12
        )));
        payload.put("shop", List.of(Map.of(
            "id", 201L,
            "name", "Toy",
            "price", 7,
            "group", "Fun",
            "frequency", Map.of("limit", 2, "period", "week"),
            "comment", "Prize",
            "money_limit", 30
        )));
        payload.put("history", List.of(Map.of(
            "id", 301L,
            "type", "earn",
            "coins", 5,
            "description", "Read",
            "taskId", 101L,
            "timestamp", timestamp.toString()
        )));
        payload.put("requests", List.of(Map.of(
            "id", 401L,
            "childId", 10,
            "taskId", 101L,
            "taskName", "Read",
            "coins", 5,
            "status", "pending",
            "requestType", "earn",
            "createdAt", timestamp.toString()
        )));

        assertThat(service.saveFamilyData("fam-1", 10, payload, true)).isInstanceOf(OperationResult.Success.class);

        verify(childRepository).updateBalance(10, 42);
        verify(familyDataRepository).markAllTasksDeleted(10);
        verify(familyDataRepository).markAllShopItemsDeleted(10);
        ArgumentCaptor<TaskUpsertCommand> taskCommandCaptor = ArgumentCaptor.forClass(TaskUpsertCommand.class);
        verify(familyDataRepository).upsertTask(taskCommandCaptor.capture());
        assertThat(taskCommandCaptor.getValue()).satisfies(command -> {
            assertThat(command.familyDbId()).isEqualTo(1);
            assertThat(command.childId()).isEqualTo(10);
            assertThat(command.taskId()).isEqualTo(101L);
            assertThat(command.name()).isEqualTo("Read");
            assertThat(command.coins()).isEqualTo(5);
            assertThat(command.groupName()).isEqualTo("Home");
            assertThat(command.comment()).isEqualTo("Daily");
            assertThat(command.moneyLimit()).isEqualTo(12);
            assertThat(command.active()).isTrue();
            assertThat(command.deleted()).isFalse();
            assertThat(command.frequency().get("limit").asInt()).isEqualTo(1);
            assertThat(command.frequency().get("period").asText()).isEqualTo("day");
        });

        ArgumentCaptor<ShopItemUpsertCommand> shopCommandCaptor =
            ArgumentCaptor.forClass(ShopItemUpsertCommand.class);
        verify(familyDataRepository).upsertShopItem(shopCommandCaptor.capture());
        assertThat(shopCommandCaptor.getValue()).satisfies(command -> {
            assertThat(command.familyDbId()).isEqualTo(1);
            assertThat(command.childId()).isEqualTo(10);
            assertThat(command.itemId()).isEqualTo(201L);
            assertThat(command.name()).isEqualTo("Toy");
            assertThat(command.price()).isEqualTo(7);
            assertThat(command.groupName()).isEqualTo("Fun");
            assertThat(command.comment()).isEqualTo("Prize");
            assertThat(command.moneyLimit()).isEqualTo(30);
            assertThat(command.active()).isTrue();
            assertThat(command.deleted()).isFalse();
            assertThat(command.frequency().get("limit").asInt()).isEqualTo(2);
            assertThat(command.frequency().get("period").asText()).isEqualTo("week");
        });

        verify(familyDataRepository, never()).replaceHistory(eq(1), eq(10), any());
        verify(familyDataRepository, never()).replaceRequests(eq(1), any());
    }

    @Test
    void saveFamilyData_existingFamily_passesActiveAndDeletedFlagsToRepository() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("children", List.of(Map.of("id", 10, "balance", 42)));
        payload.put("tasks", List.of(Map.of(
            "id", 101L,
            "name", "Read",
            "coins", 5,
            "group", "Home",
            "frequency", Map.of("limit", 1, "period", "day"),
            "is_active", false,
            "isDeleted", true
        )));
        payload.put("shop", List.of(Map.of(
            "id", 201L,
            "name", "Toy",
            "price", 7,
            "group", "Fun",
            "frequency", Map.of("limit", 2, "period", "week"),
            "isActive", false,
            "isDeleted", true
        )));

        assertThat(service.saveFamilyData("fam-1", 10, payload, true)).isInstanceOf(OperationResult.Success.class);

        verify(familyDataRepository).upsertTask(argThat(command ->
            command.familyDbId() == 1
                && command.childId() == 10
                && command.taskId() == 101L
                && "Read".equals(command.name())
                && command.coins() == 5
                && "Home".equals(command.groupName())
                && command.frequency() != null
                && command.comment() == null
                && command.moneyLimit() == null
                && !command.active()
                && command.deleted()
        ));
        verify(familyDataRepository).upsertShopItem(argThat(command ->
            command.familyDbId() == 1
                && command.childId() == 10
                && command.itemId() == 201L
                && "Toy".equals(command.name())
                && command.price() == 7
                && "Fun".equals(command.groupName())
                && command.frequency() != null
                && command.comment() == null
                && command.moneyLimit() == null
                && !command.active()
                && command.deleted()
        ));
    }

    @Test
    void saveFamilyData_adminSession_persistsFamilyRules() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.of("Screen time after homework"));
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        assertThat(service.saveFamilyData("fam-1", 10, Map.of("rules", "Screen time after homework"), true))
            .isInstanceOf(OperationResult.Success.class);

        verify(familyRepository).updateRules("fam-1", "Screen time after homework");
    }

    @Test
    void saveFamilyData_emptyHistorySnapshot_doesNotDeletePersistedTransactions() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("children", List.of(Map.of("id", 10, "balance", 10)));
        payload.put("history", List.of());

        assertThat(service.saveFamilyData("fam-1", 10, payload, true)).isInstanceOf(OperationResult.Success.class);

        verify(familyDataRepository, never()).replaceHistory(eq(1), eq(10), any());
    }

    @Test
    void saveFamilyData_emptyRequestSnapshot_doesNotDeletePersistedRequests() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("children", List.of(Map.of("id", 10, "balance", 10)));
        payload.put("requests", List.of());

        assertThat(service.saveFamilyData("fam-1", 10, payload, true)).isInstanceOf(OperationResult.Success.class);

        verify(familyDataRepository, never()).replaceRequests(eq(1), any());
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
