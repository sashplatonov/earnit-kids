package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.AddFriendRequest;
import com.sashplatonov.earnit.kids.dto.request.AddParentMembershipRequest;
import com.sashplatonov.earnit.kids.dto.request.AdjustBalanceRequest;
import com.sashplatonov.earnit.kids.dto.request.BulkActionType;
import com.sashplatonov.earnit.kids.dto.request.BulkShopItemActionRequest;
import com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.CreateChildRequest;
import com.sashplatonov.earnit.kids.dto.request.CreateRequestNoteRequest;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.dto.request.ImportShopItemRowRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportTaskRowRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.dto.request.RewardGoalRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateChildSettingsRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateOwnNicknameRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateParentMembershipRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdatePreferenceRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateThemeRequest;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.dto.response.TokenResponse;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.MembershipStatus;
import com.sashplatonov.earnit.kids.service.database.BaseDataService;
import com.sashplatonov.earnit.kids.service.family.action.FamilyActionService;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.websocket.WebSocketNotificationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyResourceTest {

    @Mock FamilyActionService familyActionService;
    @Mock FamilyService familyService;
    @Mock BaseDataService baseDataService;
    @Mock WebSocketNotificationService webSocketNotificationService;
    @Mock FamilyParentAccessService familyParentAccessService;

    private FamilyResource resource;
    private FamilyReadResource readResource;
    private FamilyChildSettingsResource childResource;
    private FamilySocialResource socialResource;
    private FamilyParentAccessResource parentResource;

    @BeforeEach
    void setUp() {
        resource = new FamilyResource(
            familyActionService,
            familyService,
            webSocketNotificationService,
            familyParentAccessService);
        readResource = new FamilyReadResource(familyService, baseDataService);
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

        Response response = resource.saveFamilyData(contextWithAuth(childAuth(10)), body);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyService, never()).saveFamilyData(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void setRewardGoal_childSession_usesAuthenticatedChild() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), false, List.of(), null, null, null, null);
        when(familyActionService.setRewardGoal("fam-1", 10, 2001L))
            .thenReturn(OperationResult.success(payload));

        Response response = resource.setRewardGoal(contextWithAuth(childAuth(10)), new RewardGoalRequest(2001L));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).setRewardGoal("fam-1", 10, 2001L);
    }

    @Test
    void setRewardGoal_adminSession_returnsUnauthorized() {
        Response response = resource.setRewardGoal(contextWithAuth(adminAuth()), new RewardGoalRequest(2001L));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyActionService, never()).setRewardGoal(anyString(), anyInt(), org.mockito.ArgumentMatchers.any());
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

        when(baseDataService.getBaseData()).thenReturn(Map.of("tasks", List.of(), "products", List.of()));
        Response ok = readResource.getBaseData(contextWithAuth(adminAuth()));
        assertThat(ok.getStatus()).isEqualTo(200);
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

        Response response = resource.saveFamilyData(contextWithAuth(adminAuth()), Map.of("childId", 10));

        assertThat(response.getStatus()).isEqualTo(400);
        verify(webSocketNotificationService, never()).notifyFamily(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeTask_adminDelegatesToActionServiceAndNotifiesFamily() {
        FamilyDataResponse payload = new FamilyDataResponse(5, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.completeTask("fam-1", 10, 1001L)).thenReturn(OperationResult.success(payload));

        Response response = resource.completeTask(contextWithAuth(adminAuth()), 1001L, 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void bulkTaskAction_adminDelegatesToActionServiceAndNotifiesFamily() {
        BulkTaskActionRequest request = new BulkTaskActionRequest(10, BulkActionType.delete, List.of(1001L, 1002L), null);
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.bulkTaskAction("fam-1", request)).thenReturn(OperationResult.success(payload));

        Response response = resource.bulkTaskAction(contextWithAuth(adminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).bulkTaskAction("fam-1", request);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "admin", "childId", 10)));
    }

    @Test
    void bulkShopItemAction_adminDelegatesToActionServiceAndNotifiesFamily() {
        BulkShopItemActionRequest request = new BulkShopItemActionRequest(10, BulkActionType.change_group, List.of(2001L, 2002L), "Big rewards");
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.bulkShopItemAction("fam-1", request)).thenReturn(OperationResult.success(payload));

        Response response = resource.bulkShopItemAction(contextWithAuth(adminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).bulkShopItemAction("fam-1", request);
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

        Response response = resource.importTasks(contextWithAuth(adminAuth()), request);

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

        Response response = resource.importShopItems(contextWithAuth(adminAuth()), request);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity()).isEqualTo(errorResponse);
    }

    @Test
    void requestTaskCompletion_childDelegatesToActionService() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), false, List.of(), 10, null, null, null);
        when(familyActionService.requestTaskCompletion("fam-1", 10, 1001L, null)).thenReturn(OperationResult.success(payload));

        Response response = resource.requestTaskCompletion(contextWithAuth(childAuth(10)), 1001L, new CreateRequestNoteRequest(null));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyActionService).requestTaskCompletion("fam-1", 10, 1001L, null);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "child", "childId", 10)));
    }

    @Test
    void adjustBalance_adminDelegatesToActionService() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), 10, null, null, null);
        when(familyActionService.adjustBalance("fam-1", 10, -3, "Manual correction"))
            .thenReturn(OperationResult.success(payload));

        Response response = resource.adjustBalance(
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

        Response response = resource.deleteRequest(contextWithAuth(childAuth(10)), 123L, 99);

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
        when(familyParentAccessService.removeMembership(7, "fam-1", "admin@test.com")).thenReturn(OperationResult.success(null));

        Response response = parentResource.removeParent(contextWithAuth(adminAuth()), 7);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).removeMembership(7, "fam-1", "admin@test.com");
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
