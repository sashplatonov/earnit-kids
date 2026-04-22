package com.sashplatonov.earnit.kids.config;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AuthRefreshCookieFilter implements ContainerResponseFilter {
    private final CookieBuilder cookieBuilder;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object payloadProperty = requestContext.getProperty(AuthFilter.AUTH_REFRESHED_PAYLOAD_PROPERTY);
        if (!(payloadProperty instanceof Map<?, ?> rawPayload) || hasAuthCookieHeader(responseContext)) {
            return;
        }

        responseContext.getHeaders().add("Set-Cookie", cookieBuilder.buildSessionCookie(toPayload(rawPayload)));
    }

    private boolean hasAuthCookieHeader(ContainerResponseContext responseContext) {
        var setCookies = responseContext.getHeaders().get("Set-Cookie");
        if (setCookies == null) {
            return false;
        }

        return setCookies.stream()
            .map(String::valueOf)
            .anyMatch(cookie -> cookie.startsWith(CookieBuilder.AUTH_COOKIE_NAME + "=")
                || cookie.startsWith(CookieBuilder.REFRESH_COOKIE_NAME + "="));
    }

    private Map<String, Object> toPayload(Map<?, ?> rawPayload) {
        var payload = new LinkedHashMap<String, Object>();
        rawPayload.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                payload.put(stringKey, value);
            }
        });
        return payload;
    }
}