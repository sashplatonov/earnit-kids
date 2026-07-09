package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyCommandServiceImplTest {

    @Mock FamilyRepository familyRepository;
    @Mock ChildRepository childRepository;
    @Mock FamilyDataRepository familyDataRepository;
    @Mock FamilyDashboardQueryService familyDashboardQueryService;

    private FamilyCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamilyCommandServiceImpl(
            familyRepository,
            childRepository,
            familyDataRepository,
            familyDashboardQueryService,
            new ObjectMapper()
        );
    }

    @Test
    void saveFamilyData_missingFamily_returnsFailure() {
        when(familyRepository.getDbId("missing")).thenReturn(Optional.empty());

        assertThat(service.saveFamilyData("missing", null, Map.of(), true))
            .isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void saveFamilyData_existingFamily_persistsRulesBalancesAndCollections() {
        ChildEntity child = child(10, 1, "Alice", 10);
        ChildEntity sibling = child(11, 1, "Bob", 15);
        FamilyDataResponse payload = new FamilyDataResponse(10, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(familyRepository.getLastSelectedChildId("fam-1")).thenReturn(Optional.of(10));
        when(childRepository.getChildren(1)).thenReturn(List.of(child, sibling));
        when(familyDashboardQueryService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        Map<String, Object> commandPayload = new LinkedHashMap<>();
        commandPayload.put("rules", "Screen time after homework");
        commandPayload.put("balance", 42);
        commandPayload.put("children", List.of(
            Map.of("id", 10, "balance", 42),
            Map.of("id", 11, "balance", 9000)
        ));
        commandPayload.put("tasks", List.of(Map.of(
            "id", 101L,
            "name", "Read",
            "coins", 5,
            "group", "Home",
            "frequency", Map.of("limit", 1, "period", "day"),
            "comment", "Daily",
            "money_limit", 12
        )));
        commandPayload.put("shop", List.of(Map.of(
            "id", 201L,
            "name", "Toy",
            "price", 7,
            "group", "Fun",
            "frequency", Map.of("limit", 2, "period", "week"),
            "comment", "Prize",
            "money_limit", 30
        )));

        OperationResult<FamilyDataResponse> result = service.saveFamilyData("fam-1", 10, commandPayload, true);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(familyRepository).updateRules("fam-1", "Screen time after homework");
        verify(childRepository).updateBalance(10, 42);
        verify(childRepository).updateBalance(11, 9000);
        verify(familyDataRepository).markAllTasksDeleted(10);
        verify(familyDataRepository).markAllShopItemsDeleted(10);
        verify(familyDashboardQueryService).loadFamilyData("fam-1", 10, true);

        ArgumentCaptor<TaskUpsertCommand> taskCommandCaptor = ArgumentCaptor.forClass(TaskUpsertCommand.class);
        verify(familyDataRepository).upsertTask(taskCommandCaptor.capture());
        assertThat(taskCommandCaptor.getValue().name()).isEqualTo("Read");

        ArgumentCaptor<ShopItemUpsertCommand> shopCommandCaptor =
            ArgumentCaptor.forClass(ShopItemUpsertCommand.class);
        verify(familyDataRepository).upsertShopItem(shopCommandCaptor.capture());
        assertThat(shopCommandCaptor.getValue().name()).isEqualTo("Toy");
    }

    @Test
    void saveFamilyData_childSession_ignoresSiblingPayloadAndDoesNotReplaceRequests() {
        ChildEntity child = child(10, 1, "Alice", 10);
        ChildEntity sibling = child(11, 1, "Bob", 15);
        when(familyRepository.getDbId("fam-1")).thenReturn(Optional.of(1));
        when(familyRepository.getRules("fam-1")).thenReturn(Optional.empty());
        when(childRepository.getChildren(1)).thenReturn(List.of(child, sibling));
        when(familyDashboardQueryService.loadFamilyData(anyString(), any(), anyBoolean()))
            .thenReturn(OperationResult.success(new FamilyDataResponse(0, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), false, List.of(), null, null, null, null)));

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
