package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.AddFriendRequest;
import com.sashplatonov.earnit.kids.dto.request.AddParentMembershipRequest;
import com.sashplatonov.earnit.kids.dto.request.AdjustBalanceRequest;
import com.sashplatonov.earnit.kids.dto.request.CreateChildRequest;
import com.sashplatonov.earnit.kids.dto.request.CreateRequestNoteRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateChildSettingsRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateOwnNicknameRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateParentMembershipRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdatePreferenceRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateThemeRequest;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.dto.response.TokenResponse;
import com.sashplatonov.earnit.kids.service.BaseDataService;
import com.sashplatonov.earnit.kids.service.FamilyActionService;
import com.sashplatonov.earnit.kids.service.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.FamilyService;
import com.sashplatonov.earnit.kids.service.WebSocketNotificationService;
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

    @BeforeEach
    void setUp() {
        resource = new FamilyResource(
            familyActionService,
            familyService,
            baseDataService,
            webSocketNotificationService,
            familyParentAccessService);
    }

    @Test
    void getFamilyData_missingAuthContext_returnsUnauthorized() {
        Response response = resource.getFamilyData(contextWithAuth(null), null);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void getFamilyData_authenticatedUser_returnsPayload() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), null, null, null, null);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        Response response = resource.getFamilyData(contextWithAuth(adminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void getFamilyData_childSession_ignoresRequestedChildId() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), false, List.of(), 10, null, null, null);
        when(familyService.loadFamilyData("fam-1", 10, false)).thenReturn(OperationResult.success(payload));

        Response response = resource.getFamilyData(contextWithAuth(childAuth(10)), 99);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).loadFamilyData("fam-1", 10, false);
    }

    @Test
    void saveFamilyData_childSession_ignoresBodyChildIdAndUsesAuthChildId() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), false, List.of(), null, null, null, null);
        when(familyService.saveFamilyData(
            anyString(), anyInt(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(OperationResult.success(payload));

        Map<String, Object> body = Map.of("foo", "bar", "childId", 99);

        Response response = resource.saveFamilyData(contextWithAuth(childAuth(10)), body);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).saveFamilyData("fam-1", 10, body, false);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("DATA_UPDATED"), eq(Map.of("by", "child", "childId", 10)));
    }

    @Test
    void getFamilyData_superAdminSession_canAccessFamilyData() {
        FamilyDataResponse payload = new FamilyDataResponse(0, null, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), null, null, null, null);
        when(familyService.loadFamilyData("fam-1", 10, true)).thenReturn(OperationResult.success(payload));

        Response response = resource.getFamilyData(contextWithAuth(superAdminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void getBaseData_missingAuthContext_returnsUnauthorized() {
        Response unauthorized = resource.getBaseData(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(baseDataService.getBaseData()).thenReturn(Map.of("tasks", List.of(), "products", List.of()));
        Response ok = resource.getBaseData(contextWithAuth(adminAuth()));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void createChild_nonAdminOrServiceFailure_returnsExpectedStatus() {
        Response unauthorized = resource.createChild(contextWithAuth(childAuth(10)), new CreateChildRequest("Kid"));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.failure("bad"));
        Response bad = resource.createChild(contextWithAuth(adminAuth()), new CreateChildRequest("Kid"));
        assertThat(bad.getStatus()).isEqualTo(400);

        ChildInfo info = new ChildInfo(1, "Kid", "token");
        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.success(info));
        Response created = resource.createChild(contextWithAuth(adminAuth()), new CreateChildRequest("Kid"));
        assertThat(created.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteChild_adminUser_returnsOk() {
        when(familyService.deleteChild("fam-1", 10)).thenReturn(OperationResult.success(null));
        Response response = resource.deleteChild(contextWithAuth(adminAuth()), 10);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_DELETED"), eq(Map.of("childId", 10)));
    }

    @Test
    void createChild_success_notifiesFamily() {
        ChildInfo info = new ChildInfo(77, "Kid", "token");
        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.success(info));

        Response response = resource.createChild(contextWithAuth(adminAuth()), new CreateChildRequest("Kid"));

        assertThat(response.getStatus()).isEqualTo(201);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 77)));
    }

    @Test
    void getChildLink_adminReturnsTokenPayload() {
        when(familyService.getChildLoginLink("fam-1", 10)).thenReturn(OperationResult.success("child-token"));

        Response response = resource.getChildLink(contextWithAuth(adminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(new TokenResponse("child-token"));
    }

    @Test
    void updateOwnNickname_childContext_delegatesToService() {
        when(familyService.updateNickname("fam-1", 10, "Alice")).thenReturn(OperationResult.success(null));

        Response response = resource.updateOwnNickname(
            contextWithAuth(childAuth(10)),
            new UpdateOwnNicknameRequest("Alice"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).updateNickname("fam-1", 10, "Alice");
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 10)));
    }

    @Test
    void updateChildSettingsPost_validRequest_delegatesToService() {
        when(familyService.updateChildSettings("fam-1", 10, "Nick", 11, 22))
            .thenReturn(OperationResult.success(null));

        Response response = resource.updateChildSettingsPost(
            contextWithAuth(adminAuth()),
            10,
            new UpdateChildSettingsRequest("Nick", 11, 22)
        );

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).updateChildSettings("fam-1", 10, "Nick", 11, 22);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 10)));
    }

    @Test
    void updateChildThemePost_validTheme_returnsOk() {
        when(familyService.updateChildTheme("fam-1", 10, "ocean")).thenReturn(OperationResult.success(null));

        Response response = resource.updateChildThemePost(
            contextWithAuth(adminAuth()),
            10,
            new UpdateThemeRequest("ocean"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(webSocketNotificationService).notifyFamily(eq("fam-1"), eq("CHILD_UPDATED"), eq(Map.of("childId", 10)));
    }

    @Test
    void updateChildTheme_childCannotUpdateAnotherChild() {
        Response response = resource.updateChildThemePost(
            contextWithAuth(childAuth(10)),
            11,
            new UpdateThemeRequest("ocean"));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(familyService, never()).updateChildTheme(anyString(), anyInt(), anyString());
    }

    @Test
    void saveFamilyData_failure_doesNotNotifyFamily() {
        when(familyService.saveFamilyData(
            anyString(), anyInt(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(OperationResult.failure("boom"));

        Response response = resource.saveFamilyData(contextWithAuth(childAuth(10)), Map.of());

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
        Response unauthorized = resource.searchUser(contextWithAuth(adminAuth()), "Alice");
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyService.searchByNickname("Alice", 10))
            .thenReturn(OperationResult.success(List.of(new FriendDto(11, "Alice", 10))));
        Response ok = resource.searchUser(contextWithAuth(childAuth(10)), "Alice");
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void addFriend_invalidOrValidRequest_returnsExpectedStatus() {
        Response bad = resource.addFriend(contextWithAuth(childAuth(10)), new AddFriendRequest(0));
        assertThat(bad.getStatus()).isEqualTo(400);

        when(familyService.addFriend("fam-1", 10, 11)).thenReturn(OperationResult.success(null));
        Response ok = resource.addFriend(contextWithAuth(childAuth(10)), new AddFriendRequest(11));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void getFriendsList_childSession_returnsData() {
        when(familyService.getFriendsData(10)).thenReturn(OperationResult.success(List.of(new FriendDto(11, "A", 2))));

        Response response = resource.getFriendsList(contextWithAuth(childAuth(10)));

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

        Response response = resource.getAnalytics(contextWithAuth(childAuth(10)), "week", 999);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).getAnalyticsData("fam-1", 10, "week");
    }

    @Test
    void getHistoryAndRequests_authenticatedUser_forwardPagination() {
        when(familyService.getHistory("fam-1", 10, 2, 15))
            .thenReturn(OperationResult.success(new PaginatedHistory(List.of(), 0, 2, 15)));
        when(familyService.getRequests("fam-1", 2, 15)).thenReturn(OperationResult.success(new PaginatedRequests(List.of(), 0, 2, 15)));

        Response history = resource.getHistory(contextWithAuth(childAuth(10)), 99, 2, 15);
        Response requests = resource.getRequests(contextWithAuth(adminAuth()), 2, 15);

        assertThat(history.getStatus()).isEqualTo(200);
        assertThat(requests.getStatus()).isEqualTo(200);
        verify(familyService).getHistory("fam-1", 10, 2, 15);
    }

    @Test
    void getRequests_childSession_returnsUnauthorized() {
        Response response = resource.getRequests(contextWithAuth(childAuth(10)), 1, 20);

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
        Response bad = resource.updatePreference(contextWithAuth(adminAuth()), new UpdatePreferenceRequest("", 1));
        assertThat(bad.getStatus()).isEqualTo(400);

        when(familyService.updatePreference("fam-1", "lastSelectedChildId", 10))
            .thenReturn(OperationResult.success(null));

        Response ok = resource.updatePreference(
            contextWithAuth(adminAuth()),
            new UpdatePreferenceRequest("lastSelectedChildId", 10));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void updatePreference_nonAdmin_returnsUnauthorized() {
        Response response = resource.updatePreference(
            contextWithAuth(childAuth(10)),
            new UpdatePreferenceRequest("lastSelectedChildId", 10));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void listParents_adminDelegatesToService() {
        when(familyParentAccessService.listMemberships("fam-1"))
            .thenReturn(OperationResult.success(List.of(new ParentMembershipDto(1, "parent@test.com", "editor", "active"))));

        Response response = resource.listParents(contextWithAuth(adminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).listMemberships("fam-1");
    }

    @Test
    void addParent_adminDelegatesToService() {
        when(familyParentAccessService.addMembership("fam-1", "parent@test.com", "editor", "admin@test.com"))
            .thenReturn(OperationResult.success(new ParentMembershipDto(1, "parent@test.com", "editor", "active")));

        Response response = resource.addParent(
            contextWithAuth(adminAuth()),
            new AddParentMembershipRequest("parent@test.com", "editor"));

        assertThat(response.getStatus()).isEqualTo(201);
        verify(familyParentAccessService).addMembership("fam-1", "parent@test.com", "editor", "admin@test.com");
    }

    @Test
    void updateParent_adminDelegatesToService() {
        when(familyParentAccessService.updateMembership(7, "viewer", "fam-1"))
            .thenReturn(OperationResult.success(new ParentMembershipDto(7, "parent@test.com", "viewer", "active")));

        Response response = resource.updateParent(
            contextWithAuth(adminAuth()),
            7,
            new UpdateParentMembershipRequest("viewer"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).updateMembership(7, "viewer", "fam-1");
    }

    @Test
    void removeParent_adminDelegatesToService() {
        when(familyParentAccessService.removeMembership(7, "fam-1")).thenReturn(OperationResult.success(null));

        Response response = resource.removeParent(contextWithAuth(adminAuth()), 7);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyParentAccessService).removeMembership(7, "fam-1");
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
