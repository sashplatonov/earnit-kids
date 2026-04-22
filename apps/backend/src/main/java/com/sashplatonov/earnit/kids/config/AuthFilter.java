package com.sashplatonov.earnit.kids.config;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;

import java.util.Map;
import java.util.Optional;

@Provider
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AuthFilter implements ContainerRequestFilter {
    public static final String AUTH_CONTEXT_PROPERTY = "auth.context";
    public static final String AUTH_REFRESHED_PAYLOAD_PROPERTY = "auth.refreshed-payload";

    private final JwtService jwtService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var cookieHeader = requestContext.getHeaderString("Cookie");
        var payloadOpt = verifyCookieToken(readCookie(cookieHeader, CookieBuilder.AUTH_COOKIE_NAME));
        var refreshedFromRefreshToken = false;
        if (payloadOpt.isEmpty()) {
            payloadOpt = verifyCookieToken(readCookie(cookieHeader, CookieBuilder.REFRESH_COOKIE_NAME));
            refreshedFromRefreshToken = payloadOpt.isPresent();
        }
        if (payloadOpt.isEmpty()) {
            return;
        }

        var resolvedPayload = payloadOpt.get();
        var cookieCsrf = readCookie(cookieHeader, CookieBuilder.CSRF_COOKIE_NAME);

        var method = requestContext.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            var headerCsrf = requestContext.getHeaderString("X-CSRF-Token");
            var expectedCsrf = resolvedPayload.get("csrfToken");
            var expected = expectedCsrf == null ? null : expectedCsrf.toString();
            if (headerCsrf == null || expected == null || !headerCsrf.equals(expected)) {
                requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ErrorResponse.of(BackendMessages.message("security.csrfInvalid")))
                        .build());
                return;
            }
        }

        var ctx = AuthContext.fromPayload(resolvedPayload, cookieCsrf);
        requestContext.setProperty(AUTH_CONTEXT_PROPERTY, ctx);
        if (refreshedFromRefreshToken) {
            requestContext.setProperty(AUTH_REFRESHED_PAYLOAD_PROPERTY, resolvedPayload);
        }
    }

    private Optional<Map<String, Object>> verifyCookieToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jwtService.verifyToken(token);
    }

    private String readCookie(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(name)) {
                return kv[1].trim();
            }
        }
        return null;
    }
}
