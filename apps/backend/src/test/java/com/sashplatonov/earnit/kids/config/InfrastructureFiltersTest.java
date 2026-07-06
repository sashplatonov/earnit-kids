package com.sashplatonov.earnit.kids.config;

import com.sashplatonov.earnit.kids.service.HttpRequestMetricsRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfrastructureFiltersTest {

    @Test
    void securityHeadersFilter_addsExpectedHeaders() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);

        assertThat(headers).containsEntry("X-Content-Type-Options", java.util.List.of("nosniff"));
        assertThat(headers).containsEntry("X-Frame-Options", java.util.List.of("DENY"));
        assertThat(headers).containsEntry("X-XSS-Protection", java.util.List.of("1; mode=block"));
        assertThat(headers).containsEntry("Referrer-Policy", java.util.List.of("no-referrer"));
        assertThat(headers).containsEntry("Cross-Origin-Resource-Policy", java.util.List.of("same-site"));
        assertThat(headers).containsEntry(
            "Strict-Transport-Security",
            java.util.List.of("max-age=31536000; includeSubDomains")
        );
    }

    @Test
    void httpRequestMetricsFilter_storesNormalizedRequestPath() {
        HttpRequestMetricsRegistry metricsRegistry = mock(HttpRequestMetricsRegistry.class);
        HttpRequestMetricsFilter filter = new HttpRequestMetricsFilter(metricsRegistry);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("api/data");

        filter.filter(request);

        verify(request).setProperty(eq("metrics.startNanos"), any(Long.class));
        verify(request).setProperty("metrics.path", "/api/data");
    }

    @Test
    void httpRequestMetricsFilter_fallsBackForMissingStartAndBlankPath() {
        HttpRequestMetricsRegistry metricsRegistry = mock(HttpRequestMetricsRegistry.class);
        HttpRequestMetricsFilter filter = new HttpRequestMetricsFilter(metricsRegistry);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        UriInfo uriInfo = mock(UriInfo.class);

        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("   ");
        when(request.getMethod()).thenReturn("POST");
        when(request.getProperty("metrics.startNanos")).thenReturn("invalid");
        when(request.getProperty("metrics.path")).thenReturn(null);
        when(response.getStatus()).thenReturn(503);

        filter.filter(request);
        filter.filter(request, response);

        verify(request).setProperty("metrics.path", "/");
        verify(metricsRegistry).record(
            eq("POST"),
            eq("/"),
            eq(503),
            longThat(durationMs -> durationMs >= 0L),
            eq(-1L)
        );
    }

    @Test
    void authRefreshCookieFilter_addsRenewedSessionCookie() {
        CookieBuilder cookieBuilder = mock(CookieBuilder.class);
        AuthRefreshCookieFilter filter = new AuthRefreshCookieFilter(cookieBuilder);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        Map<String, Object> payload = Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "a@test.com",
            "csrfToken", "csrf"
        );

        when(request.getProperty(AuthFilter.AUTH_REFRESHED_PAYLOAD_PROPERTY)).thenReturn(payload);
        when(response.getHeaders()).thenReturn(headers);
        when(cookieBuilder.buildSessionCookie(payload)).thenReturn("app_auth=renewed; Max-Age=2592000");

        filter.filter(request, response);

        assertThat(headers.get("Set-Cookie")).containsExactly("app_auth=renewed; Max-Age=2592000");
    }

    @Test
    void authRefreshCookieFilter_skipsWhenAuthCookiesAlreadyPresent() {
        CookieBuilder cookieBuilder = mock(CookieBuilder.class);
        AuthRefreshCookieFilter filter = new AuthRefreshCookieFilter(cookieBuilder);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        headers.add("Set-Cookie", "app_auth=; Max-Age=0");
        when(request.getProperty(AuthFilter.AUTH_REFRESHED_PAYLOAD_PROPERTY)).thenReturn(Map.of("familyId", "fam-1"));
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);

        verify(cookieBuilder, never()).buildSessionCookie(any());
    }
}
