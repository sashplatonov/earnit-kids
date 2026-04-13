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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
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

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success1 = (OperationResult.Success<?>) result;
        FamilyDataResponse payload = (FamilyDataResponse) success1.value();
        assertThat(payload.balance()).isZero();
        assertThat(payload.children()).isEmpty();
        assertThat(payload.tasks()).isEmpty();
    }

    @Test
    void loadFamilyData_existingChildData_mapsTasksHistoryRequestsAndFriends() {
        ChildEntity child1 = child(10, 1, "Alice", 100);
        ChildEntity child2 = child(11, 1, "Bob", 50);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(childRepository.getChildren(1)).thenReturn(List.of(child1, child2));

        TaskEntity task = TaskEntity.builder().taskId(1001L).childId(10).name("Read").coins(5).build();
        ShopItemEntity item = ShopItemEntity.builder().itemId(2001L).childId(10).name("Toy").price(7).build();
        HistoryEntryEntity history = HistoryEntryEntity.builder()
            .childId(10)
            .externalId(3001L)
            .type("earn")
            .amount(5)
            .description("Read")
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

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success2 = (OperationResult.Success<?>) result;
        FamilyDataResponse payload = (FamilyDataResponse) success2.value();
        assertThat(payload.balance()).isEqualTo(100);
        assertThat(payload.tasks()).hasSize(1);
        assertThat(payload.shop()).hasSize(1);
        assertThat(payload.history()).hasSize(1);
        assertThat(payload.requests()).hasSize(1);
        assertThat(payload.friends()).hasSize(1);
        assertThat(payload.children()).hasSize(2);
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
        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success3 = (OperationResult.Success<?>) result;
        Object val3 = success3.value();
        assertThat(val3).isInstanceOf(List.class);
        assertThat(((List<?>) val3)).isEmpty();
    }

    @Test
    void searchByNickname_matchingChildren_mapsFriendDtos() {
        when(childRepository.searchByNickname("alice", 10))
            .thenReturn(List.of(child(11, 2, "Alice 2", 17)));

        OperationResult<List<FriendDto>> result = service.searchByNickname("alice", 10);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success4 = (OperationResult.Success<?>) result;
        Object val4 = success4.value();
        assertThat(val4).isInstanceOf(List.class);
        List<?> payload = (List<?>) val4;
        assertThat(payload).singleElement().satisfies(friendObj -> {
            FriendDto friend = (FriendDto) friendObj;
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

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success5 = (OperationResult.Success<?>) result;
        Object val5 = success5.value();
        assertThat(val5).isInstanceOf(List.class);
        List<?> payload = (List<?>) val5;
        assertThat(payload).hasSize(1);
        assertThat(((FriendDto) payload.get(0)).nickname()).isEqualTo("A");
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

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success6 = (OperationResult.Success<?>) result;
        AnalyticsResponse payload = (AnalyticsResponse) success6.value();

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
            .description("Read")
            .createdAt(Instant.now())
            .build();
        when(familyDataRepository.getHistory(10, 20, 0)).thenReturn(List.of(history));
        when(familyDataRepository.getHistoryCount(10)).thenReturn(1);

        OperationResult<PaginatedHistory> result = service.getHistory(10, 1, 20);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success7 = (OperationResult.Success<?>) result;
        PaginatedHistory payload = (PaginatedHistory) success7.value();
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.total()).isEqualTo(1);
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

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        OperationResult.Success<?> success8 = (OperationResult.Success<?>) result;
        PaginatedRequests payload = (PaginatedRequests) success8.value();
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

        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", 5))
            .isInstanceOf(OperationResult.Success.class);
        assertThat(service.updatePreference("fam-1", "lastSelectedChildId", null))
            .isInstanceOf(OperationResult.Success.class);
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
}
