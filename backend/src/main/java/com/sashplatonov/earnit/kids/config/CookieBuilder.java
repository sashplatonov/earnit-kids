package com.sashplatonov.earnit.kids.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CookieBuilder {
    private final JwtService jwtService;
    private final AppConfig appConfig;

    public List<String> buildAuthCookies(String email, String role, String familyId,
                                         Integer childId, int maxAge) {
        var csrfToken = jwtService.generateCsrfToken();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("email", email);
        payload.put("role", role);
        payload.put("familyId", familyId);
        payload.put("csrfToken", csrfToken);
        if (childId != null) {
            payload.put("childId", childId);
        }

        var token = jwtService.signToken(payload, maxAge);
        var secureSegment = appConfig.production() ? "Secure; " : "";
        var authFlags = "Max-Age=" + maxAge + "; Path=/; HttpOnly; " + secureSegment;
        var roleFlags = "Max-Age=" + maxAge + "; Path=/; " + secureSegment;

        var cookies = new ArrayList<String>();
        cookies.add("app_auth=" + token + "; " + authFlags + "SameSite=Strict");
        cookies.add("app_role=" + role + "; " + roleFlags + "SameSite=Strict");
        cookies.add("csrf_token=" + csrfToken + "; " + roleFlags + "SameSite=Strict");

        if (familyId != null) {
            cookies.add("family_id=" + familyId + "; " + authFlags + "SameSite=Strict");
        }
        if (childId != null) {
            cookies.add("child_id=" + childId + "; " + authFlags + "SameSite=Strict");
        }
        return cookies;
    }

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
