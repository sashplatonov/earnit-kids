package com.sashplatonov.earnit.kids.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @AfterEach
    void tearDown() {
        MDC.clear();
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
    void filter_validRefreshToken_populatesContextAndMarksRotation() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        Map<String, Object> payload = Map.of(
            "familyId", "fam-1",
            "childId", 10,
            "role", "child",
            "permission", "child",
            "email", "c@test.com",
            "csrfToken", "payload-csrf"
        );

        when(context.getHeaderString("Cookie"))
            .thenReturn("app_auth=expired; app_refresh=refresh-good; csrf_token=cookie-csrf");
        when(context.getMethod()).thenReturn("GET");
        when(jwtService.verifyToken("expired")).thenReturn(Optional.empty());
        when(jwtService.verifyToken("refresh-good")).thenReturn(Optional.of(payload));

        filter.filter(context);

        verify(context).setProperty(org.mockito.ArgumentMatchers.eq(AuthFilter.AUTH_CONTEXT_PROPERTY),
            org.mockito.ArgumentMatchers.argThat(value -> value instanceof AuthContext auth
                && "fam-1".equals(auth.familyId())
                && Integer.valueOf(10).equals(auth.childId())
                && "child".equals(auth.role())
                && "c@test.com".equals(auth.email())
                && "cookie-csrf".equals(auth.csrfToken())));
        verify(context).setProperty(AuthFilter.AUTH_REFRESHED_PAYLOAD_PROPERTY, payload);
        assertThat(MDC.get(AuthFilter.MDC_FAMILY_ID)).isEqualTo("fam-1");
        assertThat(MDC.get(AuthFilter.MDC_CHILD_ID)).isEqualTo("10");
        assertThat(MDC.get(AuthFilter.MDC_ROLE)).isEqualTo("child");
        assertThat(MDC.get(AuthFilter.MDC_PERMISSION)).isEqualTo("child");
    }

    @Test
    void filter_validToken_populatesAuthContextAndScopeMdc() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("Cookie")).thenReturn("app_auth=good; csrf_token=cookie-csrf");
        when(jwtService.verifyToken("good")).thenReturn(Optional.of(Map.of(
            "familyId", "fam-1",
            "childId", 10,
            "role", "child",
            "permission", "family_admin",
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
                    && "family_admin".equals(auth.permission())
                    && "c@test.com".equals(auth.email())
                    && "cookie-csrf".equals(auth.csrfToken());
            }));
        assertThat(MDC.get(AuthFilter.MDC_FAMILY_ID)).isEqualTo("fam-1");
        assertThat(MDC.get(AuthFilter.MDC_CHILD_ID)).isEqualTo("10");
        assertThat(MDC.get(AuthFilter.MDC_ROLE)).isEqualTo("child");
        assertThat(MDC.get(AuthFilter.MDC_PERMISSION)).isEqualTo("family_admin");
    }
}
