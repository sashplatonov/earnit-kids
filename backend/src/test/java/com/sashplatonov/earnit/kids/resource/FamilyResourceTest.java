package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.service.BaseDataService;
import com.sashplatonov.earnit.kids.service.FamilyService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyResourceTest {

    @Mock FamilyService familyService;
    @Mock BaseDataService baseDataService;

    private FamilyResource resource;

    @BeforeEach
    void setUp() {
        resource = new FamilyResource(familyService, baseDataService);
    }

    @Test
    void getFamilyDataReturnsUnauthorizedWithoutAuthContext() {
        Response response = resource.getFamilyData(contextWithAuth(null), null);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void getFamilyDataReturnsPayloadForAuthenticatedUser() {
        FamilyDataResponse payload = new FamilyDataResponse(0, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), null, null, null, null);
        when(familyService.loadFamilyData("fam-1", 10)).thenReturn(OperationResult.success(payload));

        Response response = resource.getFamilyData(contextWithAuth(adminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(payload);
    }

    @Test
    void saveFamilyDataUsesChildIdFromPayloadOrAuth() {
        FamilyDataResponse payload = new FamilyDataResponse(0, List.of(), List.of(), List.of(), List.of(),
            List.of(), true, List.of(), null, null, null, null);
        when(familyService.saveFamilyData(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(OperationResult.success(payload));

        Response response = resource.saveFamilyData(contextWithAuth(childAuth(10)), Map.of("foo", "bar"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).saveFamilyData("fam-1", 10, Map.of("foo", "bar"));
    }

    @Test
    void getBaseDataRequiresAuthentication() {
        Response unauthorized = resource.getBaseData(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(baseDataService.getBaseData()).thenReturn(Map.of("tasks", List.of(), "products", List.of()));
        Response ok = resource.getBaseData(contextWithAuth(adminAuth()));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void createChildRequiresAdminAndValidatesServiceFailure() {
        Response unauthorized = resource.createChild(contextWithAuth(childAuth(10)), Map.of("name", "Kid"));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.failure("bad"));
        Response bad = resource.createChild(contextWithAuth(adminAuth()), Map.of("name", "Kid"));
        assertThat(bad.getStatus()).isEqualTo(400);

        ChildInfo info = new ChildInfo(1, "Kid", "token");
        when(familyService.createChild("fam-1", "Kid")).thenReturn(OperationResult.success(info));
        Response created = resource.createChild(contextWithAuth(adminAuth()), Map.of("name", "Kid"));
        assertThat(created.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteChildRequiresAdmin() {
        when(familyService.deleteChild("fam-1", 10)).thenReturn(OperationResult.success(null));
        Response response = resource.deleteChild(contextWithAuth(adminAuth()), 10);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void updateNicknameEndpointUsesChildContext() {
        when(familyService.updateNickname("fam-1", 10, "Alice")).thenReturn(OperationResult.success(null));

        Response response = resource.updateOwnNickname(contextWithAuth(childAuth(10)), Map.of("nickname", "Alice"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).updateNickname("fam-1", 10, "Alice");
    }

    @Test
    void childSettingsSupportsPostAliasAndSnakeCaseFields() {
        when(familyService.updateChildSettings("fam-1", 10, "Nick", 11, 22))
            .thenReturn(OperationResult.success(null));

        Response response = resource.updateChildSettingsPost(
            contextWithAuth(adminAuth()),
            10,
            Map.of("name", "Nick", "daily_coin_limit", 11, "monthly_limit", 22)
        );

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).updateChildSettings("fam-1", 10, "Nick", 11, 22);
    }

    @Test
    void childThemeSupportsPostAlias() {
        when(familyService.updateChildTheme(10, "ocean")).thenReturn(OperationResult.success(null));

        Response response = resource.updateChildThemePost(contextWithAuth(adminAuth()), 10, Map.of("theme", "ocean"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void searchUserRequiresChildRole() {
        Response unauthorized = resource.searchUser(contextWithAuth(adminAuth()), "Alice");
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        when(familyService.searchByNickname("Alice", 10))
            .thenReturn(OperationResult.success(List.of(new FriendDto(11, "Alice", 10))));
        Response ok = resource.searchUser(contextWithAuth(childAuth(10)), "Alice");
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void addFriendValidatesFriendId() {
        Response bad = resource.addFriend(contextWithAuth(childAuth(10)), Map.of());
        assertThat(bad.getStatus()).isEqualTo(400);

        when(familyService.addFriend("fam-1", 10, 11)).thenReturn(OperationResult.success(null));
        Response ok = resource.addFriend(contextWithAuth(childAuth(10)), Map.of("friendId", 11));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void friendsListReturnsData() {
        when(familyService.getFriendsData(10)).thenReturn(OperationResult.success(List.of(new FriendDto(11, "A", 2))));

        Response response = resource.getFriendsList(contextWithAuth(childAuth(10)));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void analyticsUsesChildIdFromChildSession() {
        when(familyService.getAnalyticsData("fam-1", 10, "week"))
            .thenReturn(OperationResult.success(Map.of("summary", Map.of())));

        Response response = resource.getAnalytics(contextWithAuth(childAuth(10)), "week", 999);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(familyService).getAnalyticsData("fam-1", 10, "week");
    }

    @Test
    void historyAndRequestsRequireAuthAndForwardPagination() {
        when(familyService.getHistory(10, 2, 15)).thenReturn(OperationResult.success(new PaginatedHistory(List.of(), 0, 2, 15)));
        when(familyService.getRequests("fam-1", 2, 15)).thenReturn(OperationResult.success(new PaginatedRequests(List.of(), 0, 2, 15)));

        Response history = resource.getHistory(contextWithAuth(childAuth(10)), null, 2, 15);
        Response requests = resource.getRequests(contextWithAuth(adminAuth()), 2, 15);

        assertThat(history.getStatus()).isEqualTo(200);
        assertThat(requests.getStatus()).isEqualTo(200);
    }

    @Test
    void preferenceEndpointHandlesMissingKeyAndSuccess() {
        Response bad = resource.updatePreference(contextWithAuth(adminAuth()), Map.of("value", 1));
        assertThat(bad.getStatus()).isEqualTo(400);

        when(familyService.updatePreference("fam-1", "lastSelectedChildId", 10))
            .thenReturn(OperationResult.success(null));

        Response ok = resource.updatePreference(contextWithAuth(adminAuth()), Map.of("key", "lastSelectedChildId", "value", 10));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf");
    }

    private static AuthContext childAuth(int childId) {
        return new AuthContext("fam-1", childId, "child", "child@test.com", "csrf");
    }
}
