package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.request.GoogleLoginRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginRequest;
import com.sashplatonov.earnit.kids.dto.request.RegisterRequest;
import com.sashplatonov.earnit.kids.dto.request.SelectFamilyRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthConfigResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.dto.response.AuthResponse;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import java.util.List;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Session, registration, and account lifecycle endpoints")
public class AuthResource {
    private final AuthService authService;
    private final CookieBuilder cookieBuilder;
    private final AppConfig appConfig;

    @Inject
    public AuthResource(AuthService authService,
                        CookieBuilder cookieBuilder,
                        AppConfig appConfig) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
        this.appConfig = appConfig;
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
    public Response login(
        @RequestBody(required = true, description = "Parent login payload") @Valid LoginRequest request) {
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
    public Response loginGoogle(
        @RequestBody(required = true, description = "Google credential payload")
        @Valid GoogleLoginRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateAdminWithGoogle(request.credential());

        return buildAdminAuthResponse(result);
    }

    private Response buildAdminAuthResponse(OperationResult<AuthPayload> result) {
        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                if (payload.selectionRequired() && payload.familyChoices() != null) {
                    List<AuthResponse.FamilyChoice> choices = payload.familyChoices().stream()
                        .map(fc -> new AuthResponse.FamilyChoice(
                            fc.familyId(), fc.familyName(), fc.permission(), fc.blocked()))
                        .toList();
                    yield Response.ok(AuthResponse.selectionRequired(choices)).build();
                }
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    payload.childId(), payload.isSuperAdmin(), payload.permission());

                Response.ResponseBuilder rb = Response.ok(
                    AuthResponse.success(payload.role(), payload.familyId()));
                cookies.forEach(c -> rb.header("Set-Cookie", c));
                yield rb.build();
            }
            case OperationResult.Failure<AuthPayload> f ->
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ErrorResponse.of(f.message(), authFailureCode(f.message()), 401))
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
    public Response loginChild(
        @RequestBody(required = true, description = "Child login payload") @Valid LoginChildRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateChild(request.token());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    payload.childId(), payload.isSuperAdmin(), payload.permission());

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
                    payload.email(), payload.role(), payload.familyId(),
                    null, payload.isSuperAdmin(), payload.permission());

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

    private String authFailureCode(String message) {
        if (BackendMessages.message("auth.familyBlocked").equals(message)) {
            return "FAMILY_BLOCKED";
        }
        if (BackendMessages.message("auth.accountBlocked").equals(message)) {
            return "ACCOUNT_BLOCKED";
        }
        return "AUTHENTICATION_FAILED";
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

    @POST
    @Path("/select-family")
    @Operation(summary = "Select active family after login with multiple memberships")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Family selected, session started",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "400", description = "Invalid selection",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response selectFamily(
        @RequestBody(required = true, description = "Family selection payload")
        @Valid SelectFamilyRequest request) {
        OperationResult<AuthPayload> result = authService.selectFamily(
            request.email(), request.familyId());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    payload.childId(), payload.isSuperAdmin(), payload.permission());

                Response.ResponseBuilder rb = Response.ok(
                    AuthResponse.success(payload.role(), payload.familyId()));
                cookies.forEach(c -> rb.header("Set-Cookie", c));
                yield rb.build();
            }
            case OperationResult.Failure<AuthPayload> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), "FAMILY_SELECTION_FAILED", 400))
                    .build();
        };
    }
}
