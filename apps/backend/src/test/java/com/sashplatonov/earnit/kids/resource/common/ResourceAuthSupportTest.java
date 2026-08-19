package com.sashplatonov.earnit.kids.resource.common;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceAuthSupportTest {

    private static final AuthContext ADMIN = new AuthContext("fam-1", null, "admin", "a@b.c", "csrf", false, "family_admin");
    private static final AuthContext CHILD = new AuthContext("fam-1", 5, "child", "c@b.c", "csrf", false, null);
    private static final AuthContext SUPER = new AuthContext("fam-1", null, "admin", "s@b.c", "csrf", true, "family_admin");

    private final TestSupport support = new TestSupport();

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static ContainerRequestContext contextWithoutAuth() {
        return mock(ContainerRequestContext.class);
    }

    @Test
    void requireAuth_withNoAuth_throws401() {
        assertThatThrownBy(() -> support.requireAuth(contextWithoutAuth()))
            .isInstanceOf(WebApplicationException.class)
            .satisfies(ex -> {
                Response response = ((WebApplicationException) ex).getResponse();
                assertThat(response.getStatus()).isEqualTo(401);
                assertThat(((ErrorResponse) response.getEntity()).errorCode()).isEqualTo("UNAUTHORIZED");
            });
    }

    @Test
    void requireAuth_withAuth_returnsAuth() {
        assertThat(support.requireAuth(contextWithAuth(ADMIN))).isEqualTo(ADMIN);
    }

    @Test
    void requireAdmin_withChildRole_throws403() {
        assertThatThrownBy(() -> support.requireAdmin(contextWithAuth(CHILD)))
            .isInstanceOf(WebApplicationException.class)
            .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
    }

    @Test
    void requireAdmin_withAdminRole_returnsAuth() {
        assertThat(support.requireAdmin(contextWithAuth(ADMIN))).isEqualTo(ADMIN);
    }

    @Test
    void requireChild_withAdminRole_throws403() {
        assertThatThrownBy(() -> support.requireChild(contextWithAuth(ADMIN)))
            .isInstanceOf(WebApplicationException.class)
            .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
    }

    @Test
    void requireChild_withChildRole_returnsAuth() {
        assertThat(support.requireChild(contextWithAuth(CHILD))).isEqualTo(CHILD);
    }

    @Test
    void requireSuperAdmin_withAdminRole_throws403() {
        assertThatThrownBy(() -> support.requireSuperAdmin(contextWithAuth(ADMIN)))
            .isInstanceOf(WebApplicationException.class)
            .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus()).isEqualTo(403));
    }

    @Test
    void requireSuperAdmin_withSuperAdmin_returnsAuth() {
        assertThat(support.requireSuperAdmin(contextWithAuth(SUPER))).isEqualTo(SUPER);
    }

    @Test
    void resolveEffectiveChildId_childSession_returnsChildId() {
        OperationResult<Integer> result = support.resolveEffectiveChildId(CHILD, null);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((OperationResult.Success<Integer>) result).value()).isEqualTo(5);
    }

    @Test
    void resolveEffectiveChildId_adminWithChildId_returnsChildId() {
        OperationResult<Integer> result = support.resolveEffectiveChildId(ADMIN, 9);
        assertThat(result.isSuccess()).isTrue();
        assertThat(((OperationResult.Success<Integer>) result).value()).isEqualTo(9);
    }

    @Test
    void resolveEffectiveChildId_adminWithoutChildId_returnsFailure() {
        OperationResult<Integer> result = support.resolveEffectiveChildId(ADMIN, null);
        assertThat(result.isSuccess()).isFalse();
    }

    private static final class TestSupport extends ResourceAuthSupport {
    }
}
