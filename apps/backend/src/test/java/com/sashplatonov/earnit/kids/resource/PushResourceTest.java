package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushResourceTest {

    private PushResource resource;

    @BeforeEach
    void setUp() {
        resource = new PushResource();
    }

    @Test
    void register_missingOrPresentAuth_returnsExpectedStatus() {
        Response unauthorized = resource.register(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        Response ok = resource.register(contextWithAuth(new AuthContext("fam-1", 10, "child", "c@test.com", "csrf", false, "child")));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void unregister_missingOrPresentAuth_returnsExpectedStatus() {
        Response unauthorized = resource.unregister(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        Response ok = resource.unregister(contextWithAuth(new AuthContext("fam-1", null, "admin", "a@test.com", "csrf", false, "family_admin")));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }
}
