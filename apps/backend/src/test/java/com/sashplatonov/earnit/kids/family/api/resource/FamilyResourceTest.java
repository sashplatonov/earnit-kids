package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.resource.FamilyChildSettingsResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyParentAccessResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyReadResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilySocialResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyTaskActionResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyShopActionResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyRequestActionResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyImportResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyHistoryResource;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyBalanceResource;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.family.api.request.AddFriendRequest;
import com.sashplatonov.earnit.kids.family.api.request.AddParentMembershipRequest;
import com.sashplatonov.earnit.kids.family.api.request.AdjustBalanceRequest;
import com.sashplatonov.earnit.kids.family.api.request.BulkActionType;
import com.sashplatonov.earnit.kids.family.api.request.BulkTaskActionRequest;
import com.sashplatonov.earnit.kids.family.api.request.ChildTheme;
import com.sashplatonov.earnit.kids.family.api.request.CreateChildRequest;
import com.sashplatonov.earnit.kids.family.api.request.CreateRequestNoteRequest;
import com.sashplatonov.earnit.kids.family.api.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.family.api.request.ImportShopItemRowRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportTaskRowRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.family.api.request.UpdateChildSettingsRequest;
import com.sashplatonov.earnit.kids.family.api.request.UpdateOwnNicknameRequest;
import com.sashplatonov.earnit.kids.family.api.request.UpdateParentMembershipRequest;
import com.sashplatonov.earnit.kids.family.api.request.UpdatePreferenceRequest;
import com.sashplatonov.earnit.kids.family.api.request.UpdateThemeRequest;
import com.sashplatonov.earnit.kids.family.api.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.family.api.response.ChildInfo;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.api.response.FriendDto;
import com.sashplatonov.earnit.kids.family.api.response.ImportValidationErrorResponse;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.dto.response.TokenResponse;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.application.catalog.LocalizedCatalogService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyCommandResourceTest {

    @Mock FamilyActionService familyActionService;
    @Mock FamilyService familyService;
    @Mock LocalizedCatalogService localizedCatalogService;
    @Mock FamilyRepository familyRepository;
    @Mock WebSocketNotificationService webSocketNotificationService;
    @Mock FamilyParentAccessService familyParentAccessService;

    private FamilyTaskActionResource taskResource;
    private FamilyShopActionResource shopResource;
    private FamilyRequestActionResource requestResource;
    private FamilyImportResource importResource;
    private FamilyHistoryResource historyResource;
    private FamilyBalanceResource balanceResource;
    private FamilyReadResource readResource;
    private FamilyChildSettingsResource childResource;
    private FamilySocialResource socialResource;
    private FamilyParentAccessResource parentResource;

    @BeforeEach
    void setUp() {
        taskResource = new FamilyTaskActionResource(
            familyActionService,
            familyService,
            webSocketNotificationService,
            familyParentAccessService);
        shopResource = new FamilyShopActionResource(familyActionService, familyService, webSocketNotificationService, familyParentAccessService);
        requestResource = new FamilyRequestActionResource(familyActionService, familyService, webSocketNotificationService, familyParentAccessService);
        importResource = new FamilyImportResource(familyActionService, familyService, webSocketNotificationService, familyParentAccessService);
        historyResource = new FamilyHistoryResource(familyActionService, familyService, webSocketNotificationService, familyParentAccessService);
        balanceResource = new FamilyBalanceResource(familyActionService, familyService, webSocketNotificationService, familyParentAccessService);
        readResource = new FamilyReadResource(familyService, localizedCatalogService);
        readResource.familyRepository = familyRepository;
        childResource = new FamilyChildSettingsResource(familyService, webSocketNotificationService, familyParentAccessService);
        socialResource = new FamilySocialResource(familyService, webSocketNotificationService, familyParentAccessService);
        parentResource = new FamilyParentAccessResource(familyService, webSocketNotificationService, familyParentAccessService);
    }

    @Test
    void getFamilyData_missingAuthContext_returnsUnauthorized() {
        Response response = readResource.getFamilyData(contextWithAuth(null), null);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void getFamilyData_authenticatedUser_returnsPayload() {
        FamilyDashboardShellResponse payload = new FamilyDashboardShellResponse(0, null, List.of(), List.of(),
            true, List.of(), null, 10, null, null, null);
        when(familyService.loadFamilyShellData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        Response response = readResource.getFamilyData(contextWithAuth(adminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void getFamilyData_childSession_ignoresRequestedChildId() {
        FamilyDashboardShellResponse payload = new FamilyDashboardShellResponse(0, null, List.of(), List.of(),
            null, List.of(), 10, 10, null, null, null);
        when(familyService.loadFamilyShellData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        Response response = readResource.getFamilyData(contextWithAuth(childAuth(10)), 99);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).loadFamilyShellData("fam-1", 10, false);
    }

    @Test
    void getFamilyDataDetails_authenticatedUser_returnsPayload() {
        FamilyDashboardDetailResponse payload = new FamilyDashboardDetailResponse(List.of(), List.of(), List.of());
        when(familyService.loadFamilyDetailData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        Response response = readResource.getFamilyDataDetails(contextWithAuth(adminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void saveFamilyData_childSession_isUnauthorized() {
        Map<String, Object> body = Map.of("foo", "bar", "childId", 99);

        Response response = taskResource.saveFamilyData(contextWithAuth(childAuth(10)), body);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyService, never()).saveFamilyData(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void getFamilyData_superAdminSession_canAccessFamilyData() {
        FamilyDashboardShellResponse payload = new FamilyDashboardShellResponse(0, null, List.of(), List.of(),
            true, List.of(), null, 10, null, null, null);
        when(familyService.loadFamilyShellData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        Response response = readResource.getFamilyData(contextWithAuth(superAdminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void getBaseData_missingAuthContext_returnsUnauthorized() {
        Response unauthorized = readResource.getBaseData(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(FamilyEntity.builder()
            .familyId("fam-1").locale(FamilyLocale.en).build()));
        when(localizedCatalogService.getBaseData(FamilyLocale.en)).thenReturn(Map.of("tasks", List.of()));
        Response ok = readResource.getBaseData(contextWithAuth(adminAuth()));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void getBaseData_usesPersistedFamilyLocaleAndDefaultsNullToEnglish() {
        when(localizedCatalogService.getBaseData(FamilyLocale.ru)).thenReturn(Map.of("locale", "ru"));
        when(localizedCatalogService.getBaseData(FamilyLocale.en)).thenReturn(Map.of("locale", "en"));
        FamilyEntity family = FamilyEntity.builder().familyId("fam-1").locale(FamilyLocale.ru).build();
        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));

        assertThat(readResource.getBaseData(contextWithAuth(adminAuth())).getEntity())
            .isEqualTo(Map.of("locale", "ru"));

        family.setLocale(null);
        assertThat(readResource.getBaseData(contextWithAuth(adminAuth())).getEntity())
            .isEqualTo(Map.of("locale", "en"));
    }

    @Test
    void createChild_nonAdminOrServiceFailure_returnsExpectedStatus() {
        Response unauthorized = childResource.createChild(contextWithAuth(childAuth(10)), new CreateChildRequest("Kid"));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.failure("bad"));
        Response bad = childResource.createChild(contextWithAuth(adminAuth()), new CreateChildRequest("Kid"));
        assertThat(bad.getStatus()).isEqualTo(400);

        ChildInfo info = new ChildInfo(1, "Kid", "token");
        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.success(info));
        Response created = childResource.createChild(contextWithAuth(adminAuth()), new CreateChildRequest("Kid"));
        assertThat(created.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteChild_adminUser_returnsOk() {
        when(familyService.deleteChild("fam-1", 10)).thenReturn(OperationResult.success(null));
        Response response = childResource.deleteChild(contextWithAuth(adminAuth()), 10);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_DELETED"), eq(Map.of("childId", 10)));
    }

    @Test
    void createChild_success_notifiesFamily() {
        ChildInfo info = new ChildInfo(77, "Kid", "token");
        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.success(info));

        Response response = childResource.createChild(contextWithAuth(adminAuth()), new CreateChildRequest("Kid"));

        assertThat(response.getStatus()).isEqualTo(201);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 77)));
    }

    @Test
    void getChildLink_adminReturnsTokenPayload() {
        when(familyService.getChildLoginLink("fam-1", 10)).thenReturn(OperationResult.success("child-token"));

        Response response = parentResource.getChildLink(contextWithAuth(adminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(new TokenResponse("child-token"));
    }

    @Test
    void updateOwnNickname_childContext_delegatesToService() {
        when(familyService.updateNickname("fam-1", 10, "Alice")).thenReturn(OperationResult.success(null));

        Response response = childResource.updateOwnNickname(
            contextWithAuth(childAuth(10)),
            new UpdateOwnNicknameRequest("Alice"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).updateNickname("fam-1", 10, "Alice");
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 10)));
    }

    @Test
    void updateChildSettingsPost_validRequest_delegatesToService() {
        when(familyService.updateChildSettings("fam-1", 10, "Nick", 11, 22, 33))
            .thenReturn(OperationResult.success(null));

        Response response = childResource.updateChildSettingsPost(
            contextWithAuth(adminAuth()),
            10,
            new UpdateChildSettingsRequest("Nick", 11, 22, 33)
        );

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).updateChildSettings("fam-1", 10, "Nick", 11, 22, 33);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 10)));
    }

    @Test
    void updateChildThemePost_validTheme_returnsOk() {
        when(familyService.updateChildTheme("fam-1", 10, ChildTheme.ocean)).thenReturn(OperationResult.success(null));

        Response response = childResource.updateChildThemePost(
            contextWithAuth(adminAuth()),
            10,
            new UpdateThemeRequest(ChildTheme.ocean));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 10)));
    }

    @Test
    void updateChildTheme_childCannotUpdateAnotherChild() {
        Response response = childResource.updateChildThemePost(
            contextWithAuth(childAuth(10)),
            11,
            new UpdateThemeRequest(ChildTheme.ocean));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyService, never()).updateChildTheme(anyString(), anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveFamilyData_failure_doesNotNotifyFamily() {
        when(familyService.saveFamilyData(
            anyString(), anyInt(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(OperationResult.failure("boom"));

        Response response = taskResource.saveFamilyData(contextWithAuth(adminAuth()), Map.of("childId", 10));

        assertThat(response.getStatus()).isEqualTo(400);
        verify(webSocketNotificationService, never()).notifyFamily(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeTask_adminDelegatesToActionServiceAndNotifiesFamily() {
        FamilyDataResponse payload = new FamilyDataResponse(5, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.completeTask("fam-1", 10, 1001L)).thenReturn(OperationResult.success(payload));

        Response response = taskResource.completeTask(contextWithAuth(adminAuth()), 1001L, 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void purchaseItem_adminDelegatesToActionServiceAndNotifiesFamily() {
        FamilyDataResponse payload = new FamilyDataResponse(5, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.purchaseItem("fam-1", 10, 2001L)).thenReturn(OperationResult.success(payload));

        Response response = shopResource.purchaseItem(contextWithAuth(adminAuth()), 2001L, 10);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).purchaseItem("fam-1", 10, 2001L);
    }

    @Test
    void purchaseItem_childSession_isUnauthorized() {
        Response response = shopResource.purchaseItem(contextWithAuth(childAuth(10)), 2001L, 10);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyActionService, never()).purchaseItem(anyString(), anyInt(), anyLong());
    }

    @Test
    void approveRequest_childSession_isUnauthorized() {
        Response response = requestResource.approveRequest(contextWithAuth(childAuth(10)), 2001L, 10);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyActionService, never()).approveRequest(anyString(), any(), anyLong());
    }

    @Test
    void bulkTaskAction_adminDelegatesToActionServiceAndNotifiesFamily() {
        BulkTaskActionRequest request = new BulkTaskActionRequest(10, BulkActionType.delete, List.of(1001L, 1002L), null);
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.bulkTaskAction("fam-1", request)).thenReturn(OperationResult.success(payload));

        Response response = taskResource.bulkTaskAction(contextWithAuth(adminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).bulkTaskAction("fam-1", request);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void importTasks_adminDelegatesToActionServiceAndNotifiesFamily() {
        ImportTasksRequest request = new ImportTasksRequest(10, List.of(
            new ImportTaskRowRequest(1, "Clean desk", 10, "Home", null, null, null, null, null, true)
        ));
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.importTasks("fam-1", request)).thenReturn(payload);

        Response response = importResource.importTasks(contextWithAuth(adminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).importTasks("fam-1", request);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void importShopItems_returnsStructuredValidationError() {
        ImportShopItemsRequest request = new ImportShopItemsRequest(10, List.of(
            new ImportShopItemRowRequest(1, "", null, null, null, null, null, null, null, null, null)
        ));
        ImportValidationErrorResponse errorResponse = ImportValidationErrorResponse.of(
            "Validation failed",
            List.of()
        );
        when(familyActionService.importShopItems("fam-1", request))
            .thenThrow(new com.sashplatonov.earnit.kids.exception.ImportValidationException(errorResponse));

        Response response = importResource.importShopItems(contextWithAuth(adminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).isEqualTo(errorResponse);
    }

    @Test
    void requestTaskCompletion_childDelegatesToActionService() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), false, List.of(), 10, null, null, null);
        when(familyActionService.requestTaskCompletion("fam-1", 10, 1001L, null)).thenReturn(OperationResult.success(payload));

        Response response = requestResource.requestTaskCompletion(contextWithAuth(childAuth(10)), 1001L, null, new CreateRequestNoteRequest(null));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).requestTaskCompletion("fam-1", 10, 1001L, null);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "child", "childId", 10)));
    }

    @Test
    void requestTaskCompletion_parentPreviewingChild_usesRequestedChildId() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.requestTaskCompletion("fam-1", 10, 1001L, null)).thenReturn(OperationResult.success(payload));

        Response response = requestResource.requestTaskCompletion(contextWithAuth(adminAuth()), 1001L, 10, new CreateRequestNoteRequest(null));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).requestTaskCompletion("fam-1", 10, 1001L, null);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void requestTaskCompletion_parentWithoutChildId_returnsBadRequest() {
        Response response = requestResource.requestTaskCompletion(contextWithAuth(adminAuth()), 1001L, null, new CreateRequestNoteRequest(null));

        assertThat(response.getStatus()).isEqualTo(400);
        verify(familyActionService, never()).requestTaskCompletion(anyString(), anyInt(), anyLong(), any());
    }

    @Test
    void requestItemPurchase_parentPreviewingChild_usesRequestedChildId() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.requestItemPurchase("fam-1", 10, 2001L, null)).thenReturn(OperationResult.success(payload));

        Response response = requestResource.requestItemPurchase(contextWithAuth(adminAuth()), 2001L, 10, new CreateRequestNoteRequest(null));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).requestItemPurchase("fam-1", 10, 2001L, null);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void requestItemPurchase_parentWithoutChildId_returnsBadRequest() {
        Response response = requestResource.requestItemPurchase(contextWithAuth(adminAuth()), 2001L, null, new CreateRequestNoteRequest(null));

        assertThat(response.getStatus()).isEqualTo(400);
        verify(familyActionService, never()).requestItemPurchase(anyString(), anyInt(), anyLong(), any());
    }

    @Test
    void deleteHistoryEntry_withoutChildId_returnsBadRequest() {
        Response response = historyResource.deleteHistoryEntry(contextWithAuth(adminAuth()), 55L, null);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(familyActionService, never()).deleteHistoryEntry(anyString(), anyInt(), anyLong());
    }

    @Test
    void adjustBalance_adminDelegatesToActionService() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.adjustBalance("fam-1", 10, -3, "Manual correction"))
            .thenReturn(OperationResult.success(payload));

        Response response = balanceResource.adjustBalance(
            contextWithAuth(adminAuth()),
            new AdjustBalanceRequest(10, -3, "Manual correction")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).adjustBalance("fam-1", 10, -3, "Manual correction");
    }

    @Test
    void searchUser_nonChildOrChildSession_returnsExpectedStatus() {
        Response unauthorized = socialResource.searchUser(contextWithAuth(adminAuth()), "Alice");
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyService.searchByNickname("Alice", 10))
            .thenReturn(OperationResult.success(List.of(new FriendDto(11, "Alice", 10))));
        Response ok = socialResource.searchUser(contextWithAuth(childAuth(10)), "Alice");
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void addFriend_invalidOrValidRequest_returnsExpectedStatus() {
        Response bad = socialResource.addFriend(contextWithAuth(childAuth(10)), new AddFriendRequest(0));
        assertThat(bad.getStatus()).isEqualTo(400);

        when(familyService.addFriend("fam-1", 10, 11)).thenReturn(OperationResult.success(null));
        Response ok = socialResource.addFriend(contextWithAuth(childAuth(10)), new AddFriendRequest(11));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void getFriendsList_childSession_returnsData() {
        when(familyService.getFriendsData(10)).thenReturn(OperationResult.success(List.of(new FriendDto(11, "A", 2))));

        Response response = socialResource.getFriendsList(contextWithAuth(childAuth(10)));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getAnalytics_childSession_usesChildIdFromContext() {
        when(familyService.getAnalyticsData("fam-1", 10, "week"))
            .thenReturn(OperationResult.success(new AnalyticsResponse(
                new AnalyticsResponse.AnalyticsSummary(1, 0, 1),
                List.of(),
                List.of(),
                List.of(),
                new AnalyticsResponse.AnalyticsSummary(0, 0, 0),
                List.of())));

        Response response = readResource.getAnalytics(contextWithAuth(childAuth(10)), "week", 999);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).getAnalyticsData("fam-1", 10, "week");
    }

    @Test
    void getHistoryAndRequests_authenticatedUser_forwardPagination() {
        when(familyService.getHistory("fam-1", 10, 2, 15))
            .thenReturn(OperationResult.success(new PaginatedHistory(List.of(), 0, 2, 15)));
        when(familyService.getRequests("fam-1", 2, 15)).thenReturn(OperationResult.success(new PaginatedRequests(List.of(), 0, 2, 15)));

        Response history = readResource.getHistory(contextWithAuth(childAuth(10)), 99, 2, 15);
        Response requests = readResource.getRequests(contextWithAuth(adminAuth()), 2, 15);

        assertThat(history.getStatus()).isEqualTo(200);
        assertThat(requests.getStatus()).isEqualTo(200);
        verify(familyService).getHistory("fam-1", 10, 2, 15);
    }

    @Test
    void getRequests_childSession_returnsUnauthorized() {
        Response response = readResource.getRequests(contextWithAuth(childAuth(10)), 1, 20);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void deleteRequest_childSession_ignoresRequestedChildIdAndUsesAuthChildId() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), false, List.of(), 10, null, null, null);
        when(familyActionService.deleteRequest("fam-1", 10, 123L)).thenReturn(OperationResult.success(payload));

        Response response = requestResource.deleteRequest(contextWithAuth(childAuth(10)), 123L, 99);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).deleteRequest("fam-1", 10, 123L);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "child", "childId", 10)));
    }

    @Test
    void updatePreference_missingKeyOrValidPayload_returnsExpectedStatus() {
        Response bad = parentResource.updatePreference(contextWithAuth(adminAuth()), new UpdatePreferenceRequest(null, 1));
        assertThat(bad.getStatus()).isEqualTo(400);

        when(familyService.updatePreference("fam-1", FamilyPreferenceKey.lastSelectedChildId, 10))
            .thenReturn(OperationResult.success(null));

        Response ok = parentResource.updatePreference(
            contextWithAuth(adminAuth()),
            new UpdatePreferenceRequest(FamilyPreferenceKey.lastSelectedChildId, 10));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void updatePreference_nonAdmin_returnsUnauthorized() {
        Response response = parentResource.updatePreference(
            contextWithAuth(childAuth(10)),
            new UpdatePreferenceRequest(FamilyPreferenceKey.lastSelectedChildId, 10));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void listParents_adminDelegatesToService() {
        when(familyParentAccessService.listMemberships("fam-1"))
            .thenReturn(OperationResult.success(List.of(new ParentMembershipDto(
                1,
                "parent@test.com",
                FamilyParentMembershipEntity.Permission.editor,
                MembershipStatus.active
            ))));

        Response response = parentResource.listParents(contextWithAuth(adminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).listMemberships("fam-1");
    }

    @Test
    void addParent_adminDelegatesToService() {
        when(familyParentAccessService.addMembership("fam-1", "parent@test.com", "editor", "admin@test.com"))
            .thenReturn(OperationResult.success(new ParentMembershipDto(
                1,
                "parent@test.com",
                FamilyParentMembershipEntity.Permission.editor,
                MembershipStatus.active
            )));

        Response response = parentResource.addParent(
            contextWithAuth(adminAuth()),
            new AddParentMembershipRequest("parent@test.com", "editor"));

        assertThat(response.getStatus()).isEqualTo(201);
        verify(familyParentAccessService).addMembership("fam-1", "parent@test.com", "editor", "admin@test.com");
    }

    @Test
    void updateParent_adminDelegatesToService() {
        when(familyParentAccessService.updateMembership(7, "viewer", "fam-1"))
            .thenReturn(OperationResult.success(new ParentMembershipDto(
                7,
                "parent@test.com",
                FamilyParentMembershipEntity.Permission.viewer,
                MembershipStatus.active
            )));

        Response response = parentResource.updateParent(
            contextWithAuth(adminAuth()),
            7,
            new UpdateParentMembershipRequest("viewer"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).updateMembership(7, "viewer", "fam-1");
    }

    @Test
    void removeParent_adminDelegatesToService() {
        when(familyParentAccessService.removeMembership(7, "fam-1", null, "admin@test.com"))
            .thenReturn(OperationResult.success(null));

        Response response = parentResource.removeParent(contextWithAuth(adminAuth()), 7);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).removeMembership(7, "fam-1", null, "admin@test.com");
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf", false, "family_admin");
    }

    private static AuthContext childAuth(int childId) {
        return new AuthContext("fam-1", childId, "child", "child@test.com", "csrf", false, "child");
    }

    private static AuthContext superAdminAuth() {
        return new AuthContext("fam-1", null, "admin", "root@test.com", "csrf", true, "family_admin");
    }
}
