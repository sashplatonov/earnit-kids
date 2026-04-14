package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
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
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyServiceImplTest {

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
            shopItemRepository
        );
    }

    @Test
    void loadFamilyData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("missing", null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void loadFamilyData_familyWithoutChildren_returnsEmptyPayload() {
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.getChildren(1)).thenReturn(List.of());

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("fam-1", null);

        FamilyDataResponse payload = successValue(result);
        assertThat(payload.balance()).isZero();
        assertThat(payload.children()).isEmpty();
        assertThat(payload.tasks()).isEmpty();
    }

    @Test
    void loadFamilyData_existingChildData_mapsTasksHistoryRequestsAndFriends() {
        ChildEntity child1 = child(10, 1, "Alice", 100);
        ChildEntity child2 = child(11, 1, "Bob", 50);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getLastSelectedChildId("fam-1")).thenReturn(Optional.of(11));
        when(childRepository.getChildren(1)).thenReturn(List.of(child1, child2));

        TaskEntity task = TaskEntity.builder().taskId(1001L).childId(10).name("Read").coins(5)
            .frequency("{\"limit\":1,\"period\":\"day\"}")
            .comment("Pages").build();
        ShopItemEntity item = ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(7)
            .frequency("{\"limit\":2,\"period\":\"week\"}")
            .comment("Prize").build();
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .childId(10)
            .externalId(3001L)
            .type("earn")
            .amount(5)
            .relatedId(1001L)
            .createdAt(Instant.now())
            .build();
        PurchaseRequestEntity request = PurchaseRequestEntity.builder()
            .id(4001L)
            .childId(10)
            .familyId(1)
            .coins(7)
            .requestType("shop")
            .build();

        when(familyDataRepository.getTasks(10)).thenReturn(List.of(task));
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of(item));
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of(history));
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of(request));
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of(11));
        when(childRepository.findByChildIds(List.of(11))).thenReturn(List.of(child2));

        OperationResult<FamilyDataResponse> result = service.loadFamilyData("fam-1", 10);

        FamilyDataResponse payload = successValue(result);
        assertThat(payload.balance()).isEqualTo(100);
        assertThat(payload.tasks()).hasSize(1);
        assertThat(payload.shop()).hasSize(1);
        assertThat(payload.history()).hasSize(1);
        assertThat(payload.requests()).hasSize(1);
        assertThat(payload.friends()).hasSize(1);
        assertThat(payload.children()).hasSize(2);
        assertThat(payload.lastSelectedChildId()).isEqualTo(11);
        assertThat(payload.history().getFirst().description()).isEqualTo("Read");
        assertThat(payload.history().getFirst().taskId()).isEqualTo(1001L);
        assertThat(payload.history().getFirst().comment()).isEqualTo("Pages");
        assertThat(payload.shop().getFirst().comment()).isEqualTo("Prize");
        assertThat(payload.tasks().getFirst().frequency()).isInstanceOf(Map.class);
        assertThat(payload.shop().getFirst().frequency()).isInstanceOf(Map.class);
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
    void updateChildTheme_unknownOrKnownTheme_returnsExpectedResult() {
        assertThat(service.updateChildTheme(10, "unknown"))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.updateChildTheme(10, "ocean"))
            .isInstanceOf(OperationResult.Success.class);
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

        Instant now = Instant.now();
        List<HistoryEntryEntity> current = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type("earn").amount(5).relatedId(1001L)
                .description("Task 1").createdAt(now.minusSeconds(3600)).build(),
            HistoryEntryEntity.builder().familyId(1).childId(10).type("spend").amount(3).relatedId(2001L)
                .description("Item 1").createdAt(now.minusSeconds(1800)).build()
        );
        List<HistoryEntryEntity> previous = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type("earn").amount(2)
                .createdAt(now.minusSeconds(60 * 60 * 24 * 40L)).build()
        );
        List<HistoryEntryEntity> monthly = List.of(
            HistoryEntryEntity.builder().familyId(1).childId(10).type("earn").amount(4)
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
    void getHistory_existingEntries_returnsPaginatedResponse() {
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .childId(10)
            .externalId(1L)
            .type("earn")
            .amount(5)
            .relatedId(1001L)
            .createdAt(Instant.now())
            .build();
        when(familyDataRepository.getHistory(10, 20, 0)).thenReturn(List.of(history));
        when(familyDataRepository.getHistoryCount(10)).thenReturn(1);
        when(familyDataRepository.getTasks(10)).thenReturn(List.of(
            TaskEntity.builder().taskId(1001L).childId(10).name("Read").coins(5).comment("Pages").build()
        ));
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of(
            ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(3).comment("Prize").build()
        ));

        OperationResult<PaginatedHistory> result = service.getHistory(10, 1, 20);

        PaginatedHistory payload = successValue(result);
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.total()).isEqualTo(1);
        assertThat(payload.items().getFirst().description()).isEqualTo("Read");
        assertThat(payload.items().getFirst().taskId()).isEqualTo(1001L);
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
            .coins(7)
            .requestType("shop")
            .build();

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyDataRepository.getRequests(1, 20, 0)).thenReturn(List.of(request));
        when(familyDataRepository.getRequestsCount(1)).thenReturn(1);

        OperationResult<PaginatedRequests> result = service.getRequests("fam-1", 1, 20);

        PaginatedRequests payload = successValue(result);
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.total()).isEqualTo(1);
    }

    @Test
    void childTokenEndpoints_missingOrValidChild_returnExpectedResults() {
        when(childRepository.findByIdOptional(10)).thenReturn(Optional.empty());
        assertThat(service.getChildLoginLink(10)).isInstanceOf(OperationResult.Failure.class);

        when(childRepository.findByIdOptional(10)).thenReturn(Optional.of(child(10, 1, "Alice", 10)));
        assertThat(service.getChildLoginLink(10)).isInstanceOf(OperationResult.Success.class);

        when(childRepository.regenerateToken(10)).thenReturn(Optional.empty());
        assertThat(service.regenerateChildToken(10)).isInstanceOf(OperationResult.Failure.class);

        when(childRepository.regenerateToken(10)).thenReturn(Optional.of("new-token"));
        assertThat(service.regenerateChildToken(10)).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void updatePreference_knownOrUnknownKey_returnsExpectedResult() {
        assertThat(service.updatePreference("fam-1", "unknown", 1))
            .isInstanceOf(OperationResult.Failure.class);

        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.findByIdOptional(5)).thenReturn(Optional.of(child(5, 1, "Alice", 0)));

        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", 5))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", "5"))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", null))
            .isInstanceOf(OperationResult.Success.class);
        when(childRepository.findByIdOptional(12)).thenReturn(Optional.of(child(12, 2, "Other", 0)));
        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", 12))
            .isInstanceOf(OperationResult.Failure.class);
        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", "bad"))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void saveFamilyData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        assertThat(service.saveFamilyData("missing", null, Map.of()))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void saveFamilyData_existingFamily_delegatesToLoadFamilyData() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        assertThat(service.saveFamilyData("fam-1", 10, Map.of()))
            .isInstanceOf(OperationResult.Success.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveFamilyData_existingFamily_persistsTasksShopHistoryAndRequests() {
        ChildEntity child = child(10, 1, "Alice", 10);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.getChildren(1)).thenReturn(List.of(child));
        when(familyDataRepository.getTasks(10)).thenReturn(List.of());
        when(familyDataRepository.getShopItems(10)).thenReturn(List.of());
        when(familyDataRepository.getHistory(10, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getRequests(1, 50, 0)).thenReturn(List.of());
        when(familyDataRepository.getFriendChildIds(10)).thenReturn(List.of());

        Instant timestamp = Instant.now().minus(Duration.ofHours(1));
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

        assertThat(service.saveFamilyData("fam-1", 10, payload)).isInstanceOf(OperationResult.Success.class);

        verify(childRepository).updateBalance(10, 42);
        verify(familyDataRepository).markAllTasksDeleted(10);
        verify(familyDataRepository).markAllShopItemsDeleted(10);
        verify(familyDataRepository).upsertTask(
            eq(1), eq(10), eq(101L), eq("Read"), eq(5), eq("Home"),
            eq("{\"limit\":1,\"period\":\"day\"}"), eq("Daily"), eq(12), eq(false)
        );
        verify(familyDataRepository).upsertShopItem(
            eq(1), eq(10), eq(201L), eq("Toy"), eq(7), eq("Fun"),
            eq("{\"limit\":2,\"period\":\"week\"}"), eq("Prize"), eq(30), eq(false)
        );

        ArgumentCaptor<List<HistoryEntryEntity>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(familyDataRepository).replaceHistory(eq(1), eq(10), historyCaptor.capture());
        assertThat(historyCaptor.getValue()).singleElement().satisfies(entry -> {
            assertThat(entry.getExternalId()).isEqualTo(301L);
            assertThat(entry.getType()).isEqualTo("earn");
            assertThat(entry.getDescription()).isEqualTo("Read");
        });

        ArgumentCaptor<List<PurchaseRequestEntity>> requestCaptor = ArgumentCaptor.forClass(List.class);
        verify(familyDataRepository).replaceRequests(eq(1), requestCaptor.capture());
        assertThat(requestCaptor.getValue()).singleElement().satisfies(entry -> {
            assertThat(entry.getExternalId()).isEqualTo(401L);
            assertThat(entry.getTaskId()).isEqualTo(101L);
            assertThat(entry.getChildId()).isEqualTo(10);
        });
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
