package com.sashplatonov.earnit.kids.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFilterTest {

    private JwtService jwtService;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new AuthFilter(jwtService);
    }

    @Test
    void filter_missingCookieHeader_doesNothing() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("Cookie")).thenReturn(null);

        filter.filter(context);

        verify(jwtService, never()).verifyToken(any());
        verify(context, never()).setProperty(any(), any());
    }

    @Test
    void filter_missingAuthCookie_doesNothing() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("Cookie")).thenReturn("session=abc");

        filter.filter(context);

        verify(jwtService, never()).verifyToken(any());
        verify(context, never()).setProperty(any(), any());
    }

    @Test
    void filter_invalidToken_doesNotPopulateContext() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("Cookie")).thenReturn("app_auth=bad; csrf_token=x");
        when(jwtService.verifyToken("bad")).thenReturn(Optional.empty());

        filter.filter(context);

        verify(context, never()).setProperty(any(), any());
    }

    @Test
    void filter_validToken_populatesAuthContext() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("Cookie")).thenReturn("app_auth=good; csrf_token=cookie-csrf");
        when(jwtService.verifyToken("good")).thenReturn(Optional.of(Map.of(
            "familyId", "fam-1",
            "childId", 10,
            "role", "child",
            "email", "c@test.com",
            "csrfToken", "payload-csrf"
        )));

        filter.filter(context);

        verify(context).setProperty(org.mockito.ArgumentMatchers.eq(AuthFilter.AUTH_CONTEXT_PROPERTY),
            org.mockito.ArgumentMatchers.argThat(value -> {
                if (!(value instanceof AuthContext auth)) {
                    return false;
                }
                return "fam-1".equals(auth.familyId())
                    && Integer.valueOf(10).equals(auth.childId())
                    && "child".equals(auth.role())
                    && "c@test.com".equals(auth.email())
                    && "cookie-csrf".equals(auth.csrfToken());
            }));
    }
}
