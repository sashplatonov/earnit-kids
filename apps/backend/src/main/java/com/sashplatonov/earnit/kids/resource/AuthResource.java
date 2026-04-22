package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.request.ChangePasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.ForgotPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.GoogleLoginRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginRequest;
import com.sashplatonov.earnit.kids.dto.request.RegisterRequest;
import com.sashplatonov.earnit.kids.dto.request.ResetPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.VerifyEmailRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthConfigResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.dto.response.AuthResponse;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.service.GoogleOAuthService;
import com.sashplatonov.earnit.kids.config.JwtService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.CookieParam;
import java.util.Map;
import java.net.URI;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Session, registration, and account lifecycle endpoints")
public class AuthResource {
    private static final String LOCAL_APP_URL = "http:" + "/" + "/localhost:5001";

    private final AuthService authService;
    private final CookieBuilder cookieBuilder;
    private final AppConfig appConfig;
    private final GoogleOAuthService googleOAuthService;
    private final JwtService jwtService;
    private final String appUrl;

    @Inject
    public AuthResource(AuthService authService,
                        CookieBuilder cookieBuilder,
                        AppConfig appConfig,
                        GoogleOAuthService googleOAuthService,
                        JwtService jwtService,
                        @ConfigProperty(name = "APP_URL", defaultValue = LOCAL_APP_URL) String appUrl) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
        this.appConfig = appConfig;
        this.googleOAuthService = googleOAuthService;
        this.jwtService = jwtService;
        this.appUrl = appUrl == null ? "" : appUrl;
    }

    public AuthResource(AuthService authService,
                        CookieBuilder cookieBuilder,
                        AppConfig appConfig) {
        this(
            authService,
            cookieBuilder,
            appConfig,
            new com.sashplatonov.earnit.kids.service.GoogleOAuthService(appConfig, new com.fasterxml.jackson.databind.ObjectMapper()),
            new com.sashplatonov.earnit.kids.config.JwtService(
                new com.sashplatonov.earnit.kids.config.JwtCompatibilityConfig() {
                    @Override public String secret() { return "test-secret"; }
                },
                new com.fasterxml.jackson.databind.ObjectMapper(),
                new com.sashplatonov.earnit.kids.util.SecureTokenGenerator(),
                new com.sashplatonov.earnit.kids.util.TimeProvider() {
                    @Override public java.time.Instant now() { return java.time.Instant.now(); }
                }
            ),
            LOCAL_APP_URL
        );
    }

    @POST
    @Path("/login")
    @Operation(summary = "Authenticate a parent or admin account")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Authenticated session started",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response login(@RequestBody(required = true, description = "Parent login payload") @Valid LoginRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateAdmin(
            request.email(), request.password());

        return buildAdminAuthResponse(result);
    }

    @POST
    @Path("/login-google")
    @Operation(summary = "Authenticate a parent account using Google Identity Services")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Authenticated session started",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response loginGoogle(@RequestBody(required = true, description = "Google credential payload")
                                @Valid GoogleLoginRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateAdminWithGoogle(request.credential());

        return buildAdminAuthResponse(result);
    }

    private Response buildAdminAuthResponse(OperationResult<AuthPayload> result) {
        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(), payload.childId());

                Response.ResponseBuilder rb = Response.ok(
                    AuthResponse.success(payload.role(), payload.familyId()));
                cookies.forEach(c -> rb.header("Set-Cookie", c));
                yield rb.build();
            }
            case OperationResult.Failure<AuthPayload> f ->
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ErrorResponse.of(f.message(), "AUTHENTICATION_FAILED", 401))
                    .build();
        };
    }

    @POST
    @Path("/login-child")
    @Operation(summary = "Authenticate a child session by magic token")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Child session started",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "401", description = "Token is invalid or expired",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response loginChild(@RequestBody(required = true, description = "Child login payload") @Valid LoginChildRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateChild(request.token());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(), payload.childId());

                Response.ResponseBuilder rb = Response.ok(
                    AuthResponse.childSuccess(
                        payload.familyId(), payload.childId(), payload.childName()));
                cookies.forEach(c -> rb.header("Set-Cookie", c));
                yield rb.build();
            }
            case OperationResult.Failure<AuthPayload> f ->
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ErrorResponse.of(f.message(), "AUTHENTICATION_FAILED", 401))
                    .build();
        };
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Clear the current auth cookies")
    @APIResponse(responseCode = "200", description = "Session cookies cleared",
        content = @Content(schema = @Schema(implementation = SimpleResponse.class)))
    public Response logout() {
        var cookies = cookieBuilder.buildLogoutCookies();
        Response.ResponseBuilder rb = Response.ok(SimpleResponse.ok());
        cookies.forEach(c -> rb.header("Set-Cookie", c));
        return rb.build();
    }

    @POST
    @Path("/register")
    @Operation(summary = "Register a new family account")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Family account created",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "409", description = "Email already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response register(@RequestBody(required = true, description = "Family registration payload")
                             @Valid RegisterRequest request) {
        OperationResult<AuthPayload> result = authService.registerFamily(
            request.email(), request.password());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(), null);

                Response.ResponseBuilder rb = Response
                    .status(Response.Status.CREATED)
                    .entity(AuthResponse.success(payload.role(), payload.familyId()));
                cookies.forEach(c -> rb.header("Set-Cookie", c));
                yield rb.build();
            }
            case OperationResult.Failure<AuthPayload> f ->
                Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.of(f.message(), "REGISTRATION_CONFLICT", 409))
                    .build();
        };
    }

    @POST
    @Path("/forgot-password")
    @Operation(summary = "Trigger password recovery flow")
    @APIResponse(responseCode = "200", description = "Request accepted",
        content = @Content(schema = @Schema(implementation = SimpleResponse.class)))
    public Response forgotPassword(@RequestBody(required = true, description = "Email for password recovery")
                                   @Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());

        return Response.ok(SimpleResponse.ok()).build();
    }

    @POST
    @Path("/change-password")
    @Operation(summary = "Change the authenticated admin password")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Password updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Password change failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response changePassword(@Context ContainerRequestContext ctx,
                                   @RequestBody(required = true, description = "Current and new parent password")
                                   @Valid ChangePasswordRequest request) {
        AuthContext auth = getAuth(ctx);
        if (auth == null || !auth.isAdmin()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
                .build();
        }

        OperationResult<Void> result = authService.changeAdminPassword(
            auth.familyId(), request.oldPassword(), request.newPassword());

        if (result instanceof OperationResult.Success<?>) {
            return Response.ok(SimpleResponse.ok()).build();
        }

        OperationResult.Failure<Void> failure = (OperationResult.Failure<Void>) result;
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(failure.message(), "PASSWORD_CHANGE_FAILED", 400))
            .build();
    }

    

    @POST
    @Path("/reset-password")
    @Operation(summary = "Complete password recovery using a reset token")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Password updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Reset token is invalid or expired",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response resetPassword(@RequestBody(required = true, description = "Password reset payload")
                                  @Valid ResetPasswordRequest request) {
        OperationResult<Void> result = authService.resetPassword(
            request.email(), request.token(), request.password());

        if (result instanceof OperationResult.Success<?>) {
            return Response.ok(SimpleResponse.ok()).build();
        }

        OperationResult.Failure<Void> failure = (OperationResult.Failure<Void>) result;
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(failure.message(), "PASSWORD_RESET_FAILED", 400))
            .build();
    }

    @POST
    @Path("/verify")
    @Operation(summary = "Confirm family email ownership")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Email verified",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Verification token is invalid or expired",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response verifyEmail(@RequestBody(required = true, description = "Email verification payload")
                                @Valid VerifyEmailRequest request) {
        OperationResult<Void> result = authService.verifyEmail(request.email(), request.token());

        if (result instanceof OperationResult.Success<?>) {
            return Response.ok(SimpleResponse.ok()).build();
        }

        OperationResult.Failure<Void> failure = (OperationResult.Failure<Void>) result;
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(failure.message(), "EMAIL_VERIFICATION_FAILED", 400))
            .build();
    }

    @GET
    @Path("/auth-config")
    @Operation(summary = "Return auth-related feature flags for the UI")
    @APIResponse(responseCode = "200", description = "Feature flags returned",
        content = @Content(schema = @Schema(implementation = AuthConfigResponse.class)))
    public Response authConfig() {
        String googleClientId = configuredGoogleOAuthClientId();

        return Response.ok(new AuthConfigResponse(
            appConfig.emailVerification().enabled(),
            appConfig.passwordRecovery().enabled(),
            googleClientId != null,
            googleClientId))
            .build();
    }

    @GET
    @Path("/login-google/url")
    @Operation(summary = "Build Google authorization URL for server-side OAuth flow")
    public Response loginGoogleUrl(@QueryParam("redirect_to") String redirectTo) {
        if (configuredGoogleOAuthClientId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(BackendMessages.message("auth.googleNotConfigured"), "GOOGLE_NOT_CONFIGURED", 400))
                .build();
        }

        String callbackUri = configuredGoogleCallbackUri();
        String redirectValue = redirectTo == null ? "/" : redirectTo;
        if (!redirectValue.startsWith("http://") && !redirectValue.startsWith("https://")) {
            redirectValue = (appUrl != null && !appUrl.isBlank() ? appUrl : "") + redirectValue;
        }
        var payload = Map.<String, Object>of("redirect", redirectValue);
        String stateToken = jwtService.signToken(payload, 300);
        String authUrl = googleOAuthService.buildAuthorizationUrl(callbackUri, stateToken);

        String secureSegment = appConfig.production() ? "Secure; " : "";
        String cookie = "oauth_state=" + stateToken + "; Max-Age=300; Path=/; HttpOnly; " + secureSegment + "SameSite=Lax";

        Response.ResponseBuilder rb = Response.ok(Map.of("url", authUrl));
        rb.header("Set-Cookie", cookie);
        return rb.build();
    }

    @GET
    @Path("/login-google/callback")
    @Operation(summary = "Handle Google OAuth2 authorization code callback and start session")
    public Response loginGoogleCallback(@QueryParam("code") String code,
                                        @QueryParam("state") String state,
                                        @CookieParam("oauth_state") String oauthStateCookie) {
        String redirectTarget = "/";
        if (oauthStateCookie == null || state == null || !state.equals(oauthStateCookie)) {
            redirectTarget = "/?error=oauth_state_mismatch";
            return Response.seeOther(java.net.URI.create(redirectTarget)).build();
        }

        var verified = jwtService.verifyToken(state);
        if (verified.isPresent() && verified.get().get("redirect") instanceof String r) {
            redirectTarget = r;
        }

        String callbackUri = configuredGoogleCallbackUri();
        var tokenRespOpt = googleOAuthService.exchangeCode(code, callbackUri);
        if (tokenRespOpt.isEmpty() || tokenRespOpt.get().id_token() == null) {
            return Response.seeOther(URI.create(redirectTarget + "?error=google_exchange_failed")).build();
        }

        String idToken = tokenRespOpt.get().id_token();
        OperationResult<AuthPayload> result = authService.authenticateAdminWithGoogle(idToken);
        if (result instanceof OperationResult.Success<AuthPayload> s) {
            AuthPayload payload = s.value();
            var cookies = cookieBuilder.buildAuthCookies(
                payload.email(), payload.role(), payload.familyId(), payload.childId());

            Response.ResponseBuilder rb = Response.seeOther(URI.create(redirectTarget));
            cookies.forEach(c -> rb.header("Set-Cookie", c));
            rb.header("Set-Cookie", "oauth_state=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
            return rb.build();
        }

        return Response.seeOther(URI.create(redirectTarget + "?error=authentication_failed")).build();
    }

    private AuthContext getAuth(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
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

    private String configuredGoogleCallbackUri() {
        return appConfig.google().redirectUri()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseGet(() -> (appUrl != null && !appUrl.isBlank() ? appUrl : "") + "/api/login-google/callback");
    }

}
