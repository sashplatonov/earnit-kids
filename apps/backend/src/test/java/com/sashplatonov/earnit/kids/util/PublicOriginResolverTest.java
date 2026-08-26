package com.sashplatonov.earnit.kids.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicOriginResolverTest {

    @Test
    void configuredOrigin_normalizesRelativeAndAbsoluteRedirects() {
        PublicOriginResolver resolver = new PublicOriginResolver("https://app.test/");
        assertThat(resolver.toAbsoluteRedirect("login", null)).isEqualTo("https://app.test/login");
        assertThat(resolver.toAbsoluteRedirect("https://other.test/callback", null))
            .isEqualTo("https://other.test/callback");
    }

    @Test
    void forwardedOrigin_isUsedWhenNoConfiguredOriginExists() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getHeaderString("X-Forwarded-Proto")).thenReturn("https");
        when(context.getHeaderString("X-Forwarded-Host")).thenReturn("app.test, proxy.test");
        assertThat(new PublicOriginResolver(null).toAbsoluteRedirect("/home", context))
            .isEqualTo("https://app.test/home");
    }

    @Test
    void requestUri_isUsedAsFallbackAndDefaultPortIsRemoved() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(context.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://app.test:443/current"));
        assertThat(new PublicOriginResolver(null).resolveAbsoluteAppUri("dashboard", context))
            .isEqualTo("https://app.test/dashboard");
    }

    @Test
    void validateLocalContinuation_acceptsApplicationPathsAndRejectsForeignForms() {
        PublicOriginResolver resolver = new PublicOriginResolver("https://app.test");

        assertThat(resolver.validateLocalContinuation("/app")).contains("/app");
        assertThat(resolver.validateLocalContinuation("/workspace")).contains("/workspace");
        assertThat(resolver.validateLocalContinuation("/en/telegram?tab=history")).contains("/en/telegram?tab=history");
        assertThat(resolver.validateLocalContinuation("https://attacker.example")).isEmpty();
        assertThat(resolver.validateLocalContinuation("//attacker.example")).isEmpty();
        assertThat(resolver.validateLocalContinuation("/%2f%2fattacker.example")).isEmpty();
        assertThat(resolver.validateLocalContinuation("/bad\npath")).isEmpty();
    }
}
