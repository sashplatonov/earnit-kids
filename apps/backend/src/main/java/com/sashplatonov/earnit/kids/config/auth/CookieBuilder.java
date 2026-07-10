package com.sashplatonov.earnit.kids.config.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sashplatonov.earnit.kids.config.AppConfig;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CookieBuilder {
    public static final String AUTH_COOKIE_NAME = "app_auth";
    public static final String REFRESH_COOKIE_NAME = "app_refresh";
    public static final String ROLE_COOKIE_NAME = "app_role";
    public static final String CSRF_COOKIE_NAME = "csrf_token";
    public static final String FAMILY_COOKIE_NAME = "family_id";
    public static final String CHILD_COOKIE_NAME = "child_id";

    private final JwtService jwtService;
    private final AppConfig appConfig;

    public List<String> buildAuthCookies(String email,
                                         String role,
                                         String familyId,
                                         Integer childId,
                                         boolean isSuperAdmin,
                                         String permission) {
        var csrfToken = jwtService.generateCsrfToken();
        var payload = buildAuthPayload(
            email,
            role,
            familyId,
            childId,
            csrfToken,
            isSuperAdmin,
            permission
        );
        var helperMaxAge = Math.max(
            appConfig.auth().sessionTtlSeconds(),
            appConfig.auth().refreshTokenTtlSeconds()
        );
        var roleFlags = buildReadableCookieFlags(helperMaxAge);

        var cookies = new ArrayList<String>();
        cookies.add(buildSessionCookie(payload));
        cookies.add(buildRefreshCookie(payload));
        if (role != null && !"child".equals(role)) {
            cookies.add(ROLE_COOKIE_NAME + "=" + role + "; " + roleFlags + "SameSite=Strict");
        }
        cookies.add(CSRF_COOKIE_NAME + "=" + csrfToken + "; " + roleFlags + "SameSite=Strict");

        if (familyId != null) {
            cookies.add(FAMILY_COOKIE_NAME + "=" + familyId + "; "
                + buildHttpOnlyCookieFlags(helperMaxAge) + "SameSite=Lax");
        }
        if (childId != null) {
            cookies.add(CHILD_COOKIE_NAME + "=" + childId + "; "
                + buildHttpOnlyCookieFlags(helperMaxAge) + "SameSite=Lax");
        }
        return cookies;
    }

    public String buildSessionCookie(Map<String, Object> payload) {
        return buildSignedCookie(AUTH_COOKIE_NAME, payload, appConfig.auth().sessionTtlSeconds());
    }

    public List<String> buildLogoutCookies() {
        return List.of(
            AUTH_COOKIE_NAME + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            REFRESH_COOKIE_NAME + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            ROLE_COOKIE_NAME + "=; Max-Age=0; Path=/; SameSite=Strict",
            FAMILY_COOKIE_NAME + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            CHILD_COOKIE_NAME + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict",
            CSRF_COOKIE_NAME + "=; Max-Age=0; Path=/; SameSite=Strict"
        );
    }

    private Map<String, Object> buildAuthPayload(String email, String role, String familyId,
                                                 Integer childId, String csrfToken, boolean isSuperAdmin,
                                                 String permission) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("email", email);
        payload.put("role", role);
        payload.put("familyId", familyId);
        payload.put("csrfToken", csrfToken);
        payload.put("isSuperAdmin", isSuperAdmin);
        payload.put("permission", permission);
        if (childId != null) {
            payload.put("childId", childId);
        }
        return payload;
    }

    private String buildRefreshCookie(Map<String, Object> payload) {
        return buildSignedCookie(REFRESH_COOKIE_NAME, payload, appConfig.auth().refreshTokenTtlSeconds());
    }

    private String buildSignedCookie(String cookieName, Map<String, Object> payload, int maxAge) {
        var token = jwtService.signToken(payload, maxAge);
        return cookieName + "=" + token + "; " + buildHttpOnlyCookieFlags(maxAge) + "SameSite=Lax";
    }

    private String buildHttpOnlyCookieFlags(int maxAge) {
        return "Max-Age=" + maxAge + "; Path=/; HttpOnly; " + secureSegment();
    }

    private String buildReadableCookieFlags(int maxAge) {
        return "Max-Age=" + maxAge + "; Path=/; " + secureSegment();
    }

    private String secureSegment() {
        return appConfig.production() ? "Secure; " : "";
    }
}
