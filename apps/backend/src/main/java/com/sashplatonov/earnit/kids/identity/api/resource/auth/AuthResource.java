package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.config.auth.JwtService;
import com.sashplatonov.earnit.kids.identity.api.request.ChangePasswordRequest;
import com.sashplatonov.earnit.kids.identity.api.request.GoogleLoginRequest;
import com.sashplatonov.earnit.kids.identity.api.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.identity.api.request.LoginRequest;
import com.sashplatonov.earnit.kids.identity.api.request.RegisterRequest;
import com.sashplatonov.earnit.kids.identity.api.request.SelectFamilyRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthConfigResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.dto.response.AuthResponse;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.shared.api.response.SimpleResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.identity.application.auth.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
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
import jakarta.ws.rs.HeaderParam;
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
    private final AuthService authService;
    private final CookieBuilder cookieBuilder;
    private final AppConfig appConfig;
    private final JwtService jwtService;

    @Inject
    public AuthResource(AuthService authService,
                        CookieBuilder cookieBuilder,
                        AppConfig appConfig,
                        JwtService jwtService) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
        this.appConfig = appConfig;
        this.jwtService = jwtService;
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
        return OperationResultResponses.toResponse(result, this::adminSuccessResponse,
            failure -> Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.of(failure.message(), authFailureCode(failure.message()), 401))
                .build());
    }

    private Response adminSuccessResponse(AuthPayload payload) {
        if (payload.selectionRequired() && payload.familyChoices() != null) {
            List<AuthResponse.FamilyChoice> choices = payload.familyChoices().stream()
                .map(fc -> new AuthResponse.FamilyChoice(
                    fc.familyId(), fc.familyName(), fc.permission(), fc.blocked()))
                .toList();
            Response.ResponseBuilder response = Response.ok(AuthResponse.selectionRequired(choices));
            if (payload.parentAccountId() != null) {
                response.header("Set-Cookie", cookieBuilder.buildFamilySelectionCookie(payload.parentAccountId()));
            }
            return response.build();
        }
        var cookies = payload.parentAccountId() == null
            ? cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                payload.childId(), payload.permission())
            : cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                payload.childId(), payload.permission(), payload.parentAccountId());

        Response.ResponseBuilder response = Response.ok(
            AuthResponse.success(payload.role(), payload.familyId()));
        cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
        return response.build();
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

        return OperationResultResponses.toResponse(result, this::childSuccessResponse,
            failure -> Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.of(failure.message(), "AUTHENTICATION_FAILED", 401))
                .build());
    }

    private Response childSuccessResponse(AuthPayload payload) {
        var cookies = cookieBuilder.buildAuthCookies(
            payload.email(), payload.role(), payload.familyId(),
            payload.childId(), payload.permission());

        Response.ResponseBuilder response = Response.ok(
            AuthResponse.childSuccess(payload.familyId(), payload.childId(), payload.childName()));
        cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
        return response.build();
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

        return OperationResultResponses.toResponse(result, this::registrationSuccessResponse,
            failure -> Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.of(failure.message(), "REGISTRATION_CONFLICT", 409))
                .build());
    }

    private Response registrationSuccessResponse(AuthPayload payload) {
        var cookies = payload.parentAccountId() == null
            ? cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                null, payload.permission())
            : cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                null, payload.permission(), payload.parentAccountId());

        Response.ResponseBuilder response = Response.status(Response.Status.CREATED)
            .entity(AuthResponse.success(payload.role(), payload.familyId()));
        cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
        return response.build();
    }

    @GET
    @Path("/auth-config")
    @Operation(summary = "Return auth-related feature flags for the UI")
    @APIResponse(responseCode = "200", description = "Feature flags returned",
        content = @Content(schema = @Schema(implementation = AuthConfigResponse.class)))
    public Response authConfig() {
        String googleClientId = configuredGoogleOAuthClientId();

        return Response.ok(new AuthConfigResponse(
            googleClientId != null,
            googleClientId))
            .build();
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
        @HeaderParam("Cookie") String cookieHeader,
        @RequestBody(required = true, description = "Family selection payload")
        @Valid SelectFamilyRequest request) {
        Integer parentAccountId = cookieBuilder.readFamilySelectionParentId(cookieHeader);
        var context = readPendingChooserContext(cookieHeader);
        if (parentAccountId == null || context.isEmpty()
            || !(context.get().get("parentAccountId") instanceof Number contextParentId)
            || contextParentId.intValue() != parentAccountId) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of("Family selection has expired", "FAMILY_SELECTION_EXPIRED", 400))
                .build();
        }
        OperationResult<AuthPayload> result = authService.selectFamily(
            parentAccountId, request.familyId());

        return OperationResultResponses.toResponse(result, this::familySelectionSuccessResponse,
            failure -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(failure.message(), "FAMILY_SELECTION_FAILED", 400))
                .build());
    }

    @Deprecated
    public Response selectFamily(SelectFamilyRequest request) {
        OperationResult<AuthPayload> result = authService.selectFamily(request.email(), request.familyId());
        return OperationResultResponses.toResponse(result, this::familySelectionSuccessResponse,
            failure -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(failure.message(), "FAMILY_SELECTION_FAILED", 400))
                .build());
    }

    private Response familySelectionSuccessResponse(AuthPayload payload) {
        var cookies = payload.parentAccountId() == null
            ? cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                payload.childId(), payload.permission())
            : cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                payload.childId(), payload.permission(), payload.parentAccountId());

        Response.ResponseBuilder response = Response.ok(
            AuthResponse.success(payload.role(), payload.familyId()));
        cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
        if (payload.parentAccountId() != null) {
            response.header("Set-Cookie", cookieBuilder.clearFamilySelectionCookie());
            response.header("Set-Cookie", clearPendingChooserCookie());
        }
        return response.build();
    }

    @GET
    @Path("/select-family")
    @Operation(summary = "Read the short-lived server-owned family selection context")
    public Response familySelectionContext(@HeaderParam("Cookie") String cookieHeader) {
        var context = readPendingChooserContext(cookieHeader);
        if (context.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.of("Family selection has expired", "FAMILY_SELECTION_EXPIRED", 401))
                .build();
        }

        List<AuthResponse.FamilyChoice> choices = new ArrayList<>();
        Object rawChoices = context.get().get("choices");
        if (rawChoices instanceof List<?> list) {
            for (Object rawChoice : list) {
                if (!(rawChoice instanceof Map<?, ?> choice)
                    || !(choice.get("familyId") instanceof String familyId)
                    || !(choice.get("familyName") instanceof String familyName)
                    || !(choice.get("permission") instanceof String permission)
                    || !(choice.get("blocked") instanceof Boolean blocked)) {
                    return Response.status(Response.Status.UNAUTHORIZED).build();
                }
                choices.add(new AuthResponse.FamilyChoice(familyId, familyName, permission, blocked));
            }
        }
        if (choices.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return Response.ok(AuthResponse.selectionRequired(choices)).build();
    }

    private java.util.Optional<Map<String, Object>> readPendingChooserContext(String cookieHeader) {
        if (cookieHeader == null) {
            return java.util.Optional.empty();
        }
        for (String part : cookieHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && "pending_family_chooser".equals(pair[0])) {
                return jwtService.verifyToken(pair[1]);
            }
        }
        return java.util.Optional.empty();
    }

    private String clearPendingChooserCookie() {
        return "pending_family_chooser=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax";
    }
}
