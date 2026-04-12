package com.sashplatonov.earnit.kids.config;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;

@Provider
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AuthFilter implements ContainerRequestFilter {
    public static final String AUTH_CONTEXT_PROPERTY = "auth.context";

    private final JwtService jwtService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var cookieHeader = requestContext.getHeaderString("Cookie");
        var token = readCookie(cookieHeader, "app_auth");
        if (token == null || token.isBlank()) {
            return;
        }

        var payloadOpt = jwtService.verifyToken(token);
        if (payloadOpt.isEmpty()) {
            return;
        }

        var resolvedPayload = payloadOpt.get();
        var cookieCsrf = readCookie(cookieHeader, "csrf_token");

        var method = requestContext.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            var headerCsrf = requestContext.getHeaderString("X-CSRF-Token");
            var expectedCsrf = resolvedPayload.get("csrfToken");
            var expected = expectedCsrf == null ? null : expectedCsrf.toString();
            if (headerCsrf == null || expected == null || !headerCsrf.equals(expected)) {
                requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ErrorResponse.of("CSRF token missing or invalid"))
                        .build());
                return;
            }
        }

        var ctx = AuthContext.fromPayload(resolvedPayload, cookieCsrf);
        requestContext.setProperty(AUTH_CONTEXT_PROPERTY, ctx);
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
