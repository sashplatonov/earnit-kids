package com.sashplatonov.earnit.kids.config;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.JwtService;

import java.util.Map;

/**
 * Extracts JWT from the app_auth cookie and places the AuthContext into the request context.
 * Does NOT reject unauthenticated requests — individual resources decide authorization.
 */
@Provider
public class AuthFilter implements ContainerRequestFilter {
    public static final String AUTH_CONTEXT_PROPERTY = "auth.context";

    private final JwtService jwtService;

    @Inject
    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String cookieHeader = requestContext.getHeaderString("Cookie");
        String token = readCookie(cookieHeader, "app_auth");
        if (token == null || token.isBlank()) {
            return;
        }

        Map<String, Object> payload = jwtService.verifyToken(token);
        if (payload == null) {
            return;
        }

        String cookieCsrf = readCookie(cookieHeader, "csrf_token");
        AuthContext ctx = AuthContext.fromPayload(payload, cookieCsrf);
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
