package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.request.ChangePinRequest;
import com.sashplatonov.earnit.kids.dto.request.ForgotPasswordRequest;
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
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
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
    private static final int AUTH_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

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
    public Response login(@RequestBody(required = true, description = "Parent login payload") @Valid LoginRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateAdmin(
            request.email(), request.pin());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    payload.childId(), AUTH_COOKIE_MAX_AGE);

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
    public Response loginChild(@RequestBody(required = true, description = "Child login payload") LoginChildRequest request) {
        OperationResult<AuthPayload> result = authService.authenticateChild(request.token());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    payload.childId(), AUTH_COOKIE_MAX_AGE);

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
            request.email(), request.adminPin());

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    null, AUTH_COOKIE_MAX_AGE);

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
        // Always return 200 to prevent email enumeration
        return Response.ok(SimpleResponse.ok()).build();
    }

    @POST
    @Path("/change-pin")
    @Operation(summary = "Change the authenticated admin PIN")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "PIN updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "PIN change failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response changePin(@Context ContainerRequestContext ctx,
                              @RequestBody(required = true, description = "Current and new parent PIN")
                              @Valid ChangePinRequest request) {
        AuthContext auth = getAuth(ctx);
        if (auth == null || !auth.isAdmin()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized("Unauthorized"))
                .build();
        }

        OperationResult<Void> result = authService.changeAdminPin(auth.familyId(), request.oldPin(), request.newPin());

        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(f.message(), "PIN_CHANGE_FAILED", 400))
                .build();
        };
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

        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), "PASSWORD_RESET_FAILED", 400))
                    .build();
        };
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

        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), "EMAIL_VERIFICATION_FAILED", 400))
                    .build();
        };
    }

    @GET
    @Path("/auth-config")
    @Operation(summary = "Return auth-related feature flags for the UI")
    @APIResponse(responseCode = "200", description = "Feature flags returned",
        content = @Content(schema = @Schema(implementation = AuthConfigResponse.class)))
    public Response authConfig() {
        return Response.ok(new AuthConfigResponse(
            appConfig.emailVerification().enabled(),
            appConfig.passwordRecovery().enabled()))
            .build();
    }

    private AuthContext getAuth(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }

}
