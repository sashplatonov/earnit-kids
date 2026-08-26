package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.config.auth.JwtService;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.family.application.invitation.ParentInvitationService;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.identity.application.auth.AuthService;
import com.sashplatonov.earnit.kids.identity.application.google.GoogleOAuthService;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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

  @Inject ParentInvitationService parentInvitationService;

  @Inject
  public AuthGoogleResource(
      AuthService authService,
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
  public Response loginGoogleUrl(
      @Context ContainerRequestContext request,
      @QueryParam("redirect_to") String redirectTo,
      @CookieParam("invite_flow") String inviteFlow,
      @CookieParam("invite_continuation") Integer continuationId) {
    if (configuredGoogleOAuthClientId() == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              ErrorResponse.of(
                  BackendMessages.message("auth.googleNotConfigured"),
                  "GOOGLE_NOT_CONFIGURED",
                  400))
          .build();
    }

    String callbackUri = configuredGoogleCallbackUri(request);
    String redirectValue = publicOriginResolver.validateLocalContinuation(redirectTo).orElse(null);
    if (redirectValue == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ErrorResponse.of("Invalid OAuth continuation", "INVALID_REDIRECT", 400))
          .build();
    }
    Map<String, Object> payload = new HashMap<>();
    payload.put("redirect", redirectValue);
    if (inviteFlow != null && continuationId != null) {
      payload.put("continuationId", continuationId);
    }
    String stateToken = jwtService.signToken(payload, 300);
    String authUrl = googleOAuthService.buildAuthorizationUrl(callbackUri, stateToken);

    String secureSegment = appConfig.production() ? "Secure; " : "";
    String cookie =
        "oauth_state="
            + stateToken
            + "; Max-Age=300; Path=/; HttpOnly; "
            + secureSegment
            + "SameSite=Lax";

    Response.ResponseBuilder rb = Response.ok(Map.of("url", authUrl));
    rb.header("Set-Cookie", cookie);
    return rb.build();
  }

  @GET
  @Path("/login-google/start")
  @Operation(summary = "Redirect browser to Google authorization")
  public Response loginGoogleStart(
      @Context ContainerRequestContext request,
      @QueryParam("continue") String redirectTo,
      @CookieParam("invite_flow") String inviteFlow,
      @CookieParam("invite_continuation") Integer continuationId) {
    Response authorization = loginGoogleUrl(request, redirectTo, inviteFlow, continuationId);
    if (authorization.getStatus() != Response.Status.OK.getStatusCode()) {
      String fallback =
          publicOriginResolver.toAbsoluteRedirect(
              "/?error=google_start_failed", request);
      return Response.seeOther(URI.create(fallback))
          .header("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
          .header("Pragma", "no-cache")
          .build();
    }

    Map<?, ?> entity = (Map<?, ?>) authorization.getEntity();
    Response.ResponseBuilder redirect =
        Response.seeOther(URI.create(String.valueOf(entity.get("url"))));
    authorization
        .getHeaders()
        .get("Set-Cookie")
        .forEach(cookie -> redirect.header("Set-Cookie", cookie));
    return redirect.build();
  }

  public Response loginGoogleUrl(ContainerRequestContext request, String redirectTo) {
    return loginGoogleUrl(request, redirectTo, null, null);
  }

  @GET
  @Path("/login-google/callback")
  @Operation(summary = "Handle Google OAuth2 authorization code callback and start session")
  public Response loginGoogleCallback(
      @Context ContainerRequestContext request,
      @QueryParam("code") String code,
      @QueryParam("state") String state,
      @CookieParam("oauth_state") String oauthStateCookie,
      @CookieParam("invite_flow") String inviteFlow) {
    if (oauthStateCookie == null || state == null || !state.equals(oauthStateCookie)) {
      return redirect(request, "/?error=oauth_state_mismatch");
    }
    var verified = jwtService.verifyToken(state);
    String redirectTarget = callbackRedirect(verified);
    var tokenRespOpt = googleOAuthService.exchangeCode(code, configuredGoogleCallbackUri(request));
    if (tokenRespOpt.isEmpty() || tokenRespOpt.get().id_token() == null) {
      return redirect(request, appendError(redirectTarget, "google_exchange_failed"));
    }
    OperationResult<AuthPayload> result =
        authService.authenticateAdminWithGoogle(tokenRespOpt.get().id_token());
    if (result instanceof OperationResult.Success<AuthPayload> s) {
      return authenticatedRedirect(request, redirectTarget, inviteFlow, verified, s.value());
    }
    return redirect(request, appendError(redirectTarget, "authentication_failed"));
  }

  private String callbackRedirect(Optional<Map<String, Object>> verified) {
    return verified
        .filter(data -> data.get("redirect") instanceof String)
        .map(data -> publicOriginResolver.validateLocalContinuation((String) data.get("redirect")))
        .flatMap(value -> value)
        .orElse("/");
  }

  private Response authenticatedRedirect(
      ContainerRequestContext request,
      String redirectTarget,
      String inviteFlow,
      Optional<Map<String, Object>> verified,
      AuthPayload payload) {
    if (!invitationIsValid(inviteFlow, verified, payload)) {
      return redirect(request, appendError(redirectTarget, "invitation_flow_invalid"));
    }
    if (payload.selectionRequired() && payload.familyChoices() != null) {
      return familySelectionRedirect(request, payload);
    }
    Response.ResponseBuilder result =
        Response.seeOther(URI.create(publicOriginResolver.toAbsoluteRedirect(redirectTarget, request)));
    authCookies(payload).forEach(cookie -> result.header("Set-Cookie", cookie));
    result.header("Set-Cookie", "oauth_state=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
    return result.build();
  }

  private boolean invitationIsValid(
      String inviteFlow, Optional<Map<String, Object>> verified, AuthPayload payload) {
    Object continuation = verified.map(data -> data.get("continuationId")).orElse(null);
    return !(continuation instanceof Number id
        && parentInvitationService != null
        && (inviteFlow == null
            || !parentInvitationService.consumeOAuth(id.intValue(), inviteFlow, payload.email())));
  }

  private Response familySelectionRedirect(ContainerRequestContext request, AuthPayload payload) {
    Response.ResponseBuilder result =
        Response.seeOther(URI.create(publicOriginResolver.toAbsoluteRedirect("/select-family", request)));
    result.header("Set-Cookie", buildPendingChooserCookie(payload));
    if (payload.parentAccountId() != null) {
      result.header("Set-Cookie", cookieBuilder.buildFamilySelectionCookie(payload.parentAccountId()));
    }
    result.header("Set-Cookie", "oauth_state=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
    return result.build();
  }

  private java.util.List<String> authCookies(AuthPayload payload) {
    return payload.parentAccountId() == null
        ? cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
            payload.childId(), payload.permission())
        : cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
            payload.childId(), payload.permission(), payload.parentAccountId());
  }

  private Response redirect(ContainerRequestContext request, String target) {
    return Response.seeOther(URI.create(publicOriginResolver.toAbsoluteRedirect(target, request))).build();
  }

  public Response loginGoogleCallback(
      ContainerRequestContext request, String code, String state, String oauthStateCookie) {
    return loginGoogleCallback(request, code, state, oauthStateCookie, null);
  }

  private String configuredGoogleOAuthClientId() {
    if (!appConfig.google().enabled()) {
      return null;
    }

    String clientId =
        appConfig
            .google()
            .clientId()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElse(null);
    String clientSecret =
        appConfig
            .google()
            .clientSecret()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElse(null);

    if (clientId == null || clientSecret == null) {
      return null;
    }

    return clientId;
  }

  private String configuredGoogleCallbackUri(ContainerRequestContext request) {
    return appConfig
        .google()
        .redirectUri()
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .orElseGet(
            () ->
                publicOriginResolver.resolveAbsoluteAppUri("/api/login-google/callback", request));
  }

  private String appendError(String redirectTarget, String error) {
    return redirectTarget + (redirectTarget.contains("?") ? "&" : "?") + "error=" + error;
  }

  private String buildPendingChooserCookie(AuthPayload payload) {
    var choices = new ArrayList<Map<String, Object>>();
    payload
        .familyChoices()
        .forEach(
            choice ->
                choices.add(
                    Map.of(
                        "familyId", choice.familyId(),
                        "familyName", choice.familyName(),
                        "permission", choice.permission(),
                        "blocked", choice.blocked())));
    Map<String, Object> context = new HashMap<>();
    context.put("email", payload.email());
    context.put("parentAccountId", payload.parentAccountId());
    context.put("choices", choices);
    String encoded = jwtService.signToken(context, 300);
    String secureSegment = appConfig.production() ? "Secure; " : "";
    return "pending_family_chooser="
        + encoded
        + "; Max-Age=300; Path=/; "
        + "HttpOnly; "
        + secureSegment
        + "SameSite=Lax";
  }
}
