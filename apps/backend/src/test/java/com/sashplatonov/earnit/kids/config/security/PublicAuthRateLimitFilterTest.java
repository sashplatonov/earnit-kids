package com.sashplatonov.earnit.kids.config.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicAuthRateLimitFilterTest {
    @Test
    void rejectsAfterThresholdAndProvidesRetryHint() throws Exception {
        InboundRateLimiter limiter = new InboundRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(limiter, true, 2, 60);
        ContainerRequestContext request = request("GET", "/login-child/token", "203.0.113.10");

        filter.filter(request);
        filter.filter(request);
        filter.filter(request);

        var response = capturedResponse(request);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeaderString("Retry-After")).isEqualTo("60");
        response.close();
    }

    @Test
    void separatesRoutesAndClientsAndAllowsRequestsBelowThreshold() throws Exception {
        InboundRateLimiter limiter = new InboundRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(limiter, true, 1, 60);
        ContainerRequestContext child = request("GET", "/login-child/token", "203.0.113.10");
        ContainerRequestContext telegram = request("POST", "/api/telegram/auth/exchange", "203.0.113.10");
        ContainerRequestContext otherClient = request("GET", "/login-child/token", "203.0.113.11");

        filter.filter(child);
        filter.filter(telegram);
        filter.filter(otherClient);

        verify(child, org.mockito.Mockito.never()).abortWith(org.mockito.ArgumentMatchers.any());
        verify(telegram, org.mockito.Mockito.never()).abortWith(org.mockito.ArgumentMatchers.any());
        verify(otherClient, org.mockito.Mockito.never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetsAfterWindowExpiry() {
        InboundRateLimiter limiter = new InboundRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(limiter.tryAcquire("route", "client", 1, 60).allowed()).isTrue();
        assertThat(limiter.tryAcquire("route", "client", 1, 60).allowed()).isFalse();

        InboundRateLimiter nextWindow = new InboundRateLimiter(
            Clock.fixed(Instant.ofEpochSecond(60), ZoneOffset.UTC));
        assertThat(nextWindow.tryAcquire("route", "client", 1, 60).allowed()).isTrue();
    }

    @Test
    void protectsEveryConfiguredPublicAuthenticationRoute() throws Exception {
        String[][] routes = {
            {"GET", "/invite/parent/token"},
            {"GET", "/api/login-google/url"},
            {"GET", "/api/login-google/callback"}
        };
        for (String[] route : routes) {
            InboundRateLimiter limiter = new InboundRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(limiter, true, 1, 60);
            ContainerRequestContext request = request(route[0], route[1], "203.0.113.12");

            filter.filter(request);
            filter.filter(request);

            assertThat(capturedResponse(request).getStatus()).isEqualTo(429);
        }
    }

    private ContainerRequestContext request(String method, String path, String address) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://backend" + path));
        when(request.getHeaderString("X-Forwarded-For")).thenReturn(address);
        return request;
    }

    private Response capturedResponse(ContainerRequestContext request) {
        var captor = org.mockito.ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(captor.capture());
        return captor.getValue();
    }
}
