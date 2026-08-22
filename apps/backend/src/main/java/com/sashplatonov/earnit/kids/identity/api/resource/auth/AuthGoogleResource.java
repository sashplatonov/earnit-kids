package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.config.auth.JwtService;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.identity.application.auth.AuthService;
import com.sashplatonov.earnit.kids.identity.application.google.GoogleOAuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.PublicOriginResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Google OAuth endpoints")
public class AuthGoogleResource {
    private final AuthService authService;
    private final CookieBuilder cookieBuilder;
    private final AppConfig appConfig;
    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final PublicOriginResolver publicOriginResolver;

    @Inject
    public AuthGoogleResource(AuthService authService,
                              CookieBuilder cookieBuilder,
                              AppConfig appConfig,
                              GoogleOAuthService googleOAuthService,
                              JwtService jwtService,
                              @ConfigProperty(name = "APP_URL") Optional<String> appUrl) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
        this.appConfig = appConfig;
        this.googleOAuthService = googleOAuthService;
        this.jwtService = jwtService;
        this.publicOriginResolver = new PublicOriginResolver(appUrl.orElse(null));
    }

    @GET
    @Path("/login-google/url")
    @Operation(summary = "Build Google authorization URL for server-side OAuth flow")
    public Response loginGoogleUrl(@Context ContainerRequestContext request,
                                   @QueryParam("redirect_to") String redirectTo) {
        if (configuredGoogleOAuthClientId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(
                    BackendMessages.message("auth.googleNotConfigured"),
                    "GOOGLE_NOT_CONFIGURED", 400))
                .build();
        }

        String callbackUri = configuredGoogleCallbackUri(request);
        String redirectValue = publicOriginResolver.toAbsoluteRedirect(redirectTo, request);
        var payload = Map.<String, Object>of("redirect", redirectValue);
        String stateToken = jwtService.signToken(payload, 300);
        String authUrl = googleOAuthService.buildAuthorizationUrl(callbackUri, stateToken);

        String secureSegment = appConfig.production() ? "Secure; " : "";
        String cookie = "oauth_state=" + stateToken
            + "; Max-Age=300; Path=/; HttpOnly; " + secureSegment + "SameSite=Lax";

        Response.ResponseBuilder rb = Response.ok(Map.of("url", authUrl));
        rb.header("Set-Cookie", cookie);
        return rb.build();
    }

    @GET
    @Path("/login-google/callback")
    @Operation(summary = "Handle Google OAuth2 authorization code callback and start session")
    public Response loginGoogleCallback(@Context ContainerRequestContext request,
                                        @QueryParam("code") String code,
                                        @QueryParam("state") String state,
                                        @CookieParam("oauth_state") String oauthStateCookie) {
        String redirectTarget = "/";
        if (oauthStateCookie == null || state == null || !state.equals(oauthStateCookie)) {
            redirectTarget = "/?error=oauth_state_mismatch";
            String abs = publicOriginResolver.toAbsoluteRedirect(redirectTarget, request);
            return Response.seeOther(URI.create(abs)).build();
        }

        var verified = jwtService.verifyToken(state);
        if (verified.isPresent() && verified.get().get("redirect") instanceof String r) {
            redirectTarget = r;
        }

        String callbackUri = configuredGoogleCallbackUri(request);
        var tokenRespOpt = googleOAuthService.exchangeCode(code, callbackUri);
        if (tokenRespOpt.isEmpty() || tokenRespOpt.get().id_token() == null) {
            String abs = publicOriginResolver.toAbsoluteRedirect(
                redirectTarget + "?error=google_exchange_failed", request);
            return Response.seeOther(URI.create(abs)).build();
        }

        String idToken = tokenRespOpt.get().id_token();
        OperationResult<AuthPayload> result = authService.authenticateAdminWithGoogle(idToken);
        if (result instanceof OperationResult.Success<AuthPayload> s) {
            AuthPayload payload = s.value();
            if (payload.selectionRequired() && payload.familyChoices() != null) {
                String chooserCookie = buildPendingChooserCookie(payload);
                Response.ResponseBuilder rb = Response.seeOther(
                    URI.create(publicOriginResolver.toAbsoluteRedirect(
                        deriveLoginRedirectTarget(redirectTarget), request)));
                rb.header("Set-Cookie", chooserCookie);
                rb.header("Set-Cookie", "oauth_state=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
                return rb.build();
            }
            var cookies = cookieBuilder.buildAuthCookies(
                payload.email(), payload.role(), payload.familyId(),
                payload.childId(), payload.permission());

            Response.ResponseBuilder rb = Response.seeOther(
                URI.create(publicOriginResolver.toAbsoluteRedirect(redirectTarget, request)));
            cookies.forEach(c -> rb.header("Set-Cookie", c));
            rb.header("Set-Cookie", "oauth_state=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
            return rb.build();
        }
        return Response.seeOther(
            URI.create(publicOriginResolver.toAbsoluteRedirect(
                redirectTarget + "?error=authentication_failed", request))).build();
    }

    private String configuredGoogleOAuthClientId() {
        if (!appConfig.google().enabled()) {
            return null;
        }

        String clientId = appConfig.google().clientId()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElse(null);
        String clientSecret = appConfig.google().clientSecret()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElse(null);

        if (clientId == null || clientSecret == null) {
            return null;
        }

        return clientId;
    }

    private String configuredGoogleCallbackUri(ContainerRequestContext request) {
        return appConfig.google().redirectUri()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseGet(() -> publicOriginResolver.resolveAbsoluteAppUri("/api/login-google/callback", request));
    }

    private String deriveLoginRedirectTarget(String redirectTarget) {
        if (redirectTarget == null || redirectTarget.isBlank()) {
            return "/login";
        }

        try {
            URI uri = URI.create(redirectTarget);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "/login";
            }
            if ("/telegram".equals(path)) {
                return "/login";
            }
            if (path.endsWith("/telegram")) {
                return path.substring(0, path.length() - 9) + "/login";
            }
        } catch (IllegalArgumentException ignored) {
        }

        return "/login";
    }

    private String buildPendingChooserCookie(AuthPayload payload) {
        String raw = payload.email() + "\n" + payload.familyChoices().stream()
            .map(choice -> choice.familyId()
                + "|" + choice.familyName()
                + "|" + choice.permission()
                + "|" + choice.blocked())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        String secureSegment = appConfig.production() ? "Secure; " : "";
        return "pending_family_chooser=" + encoded + "; Max-Age=300; Path=/; "
            + secureSegment + "SameSite=Lax";
    }
}
