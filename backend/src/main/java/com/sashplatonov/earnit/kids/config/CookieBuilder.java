package com.sashplatonov.earnit.kids.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CookieBuilder {
    private final JwtService jwtService;
    private final boolean isProduction;

    @Inject
    public CookieBuilder(JwtService jwtService,
            @ConfigProperty(name = "app.production", defaultValue = "false") boolean isProduction) {
        this.jwtService = jwtService;
        this.isProduction = isProduction;
    }

    /**
     * Builds Set-Cookie header values for a successful authentication.
     *
     * @param email    user email
     * @param role     user role (admin, child, super_admin)
     * @param familyId family identifier
     * @param childId  child id (nullable)
     * @param maxAge   cookie lifetime in seconds
     * @return list of Set-Cookie header strings
     */
    public List<String> buildAuthCookies(String email, String role, String familyId,
                                         Integer childId, int maxAge) {
        String csrfToken = jwtService.generateCsrfToken();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("role", role);
        payload.put("familyId", familyId);
        payload.put("csrfToken", csrfToken);
        if (childId != null) {
            payload.put("childId", childId);
        }

        String token = jwtService.signToken(payload, maxAge);
        String secureSegment = isProduction ? "Secure; " : "";
        String authFlags = "Max-Age=" + maxAge + "; Path=/; HttpOnly; " + secureSegment;
        String roleFlags = "Max-Age=" + maxAge + "; Path=/; " + secureSegment;

        List<String> cookies = new ArrayList<>();
        cookies.add("app_auth=" + token + "; " + authFlags + "SameSite=Lax");
        cookies.add("app_role=" + role + "; " + roleFlags + "SameSite=Lax");
        cookies.add("csrf_token=" + csrfToken + "; " + roleFlags + "SameSite=Strict");

        if (familyId != null) {
            cookies.add("family_id=" + familyId + "; " + authFlags + "SameSite=Lax");
        }
        if (childId != null) {
            cookies.add("child_id=" + childId + "; " + authFlags + "SameSite=Lax");
        }
        return cookies;
    }

    /**
     * Builds Set-Cookie headers that clear all auth cookies.
     *
     * @return list of Set-Cookie header strings with Max-Age=0
     */
    public List<String> buildLogoutCookies() {
        return List.of(
            "app_auth=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            "app_role=; Max-Age=0; Path=/; SameSite=Strict",
            "family_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            "child_id=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            "csrf_token=; Max-Age=0; Path=/; SameSite=Strict"
        );
    }
}
