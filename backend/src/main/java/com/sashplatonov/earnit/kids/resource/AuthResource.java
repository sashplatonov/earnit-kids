package com.sashplatonov.earnit.kids.resource;

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
import com.sashplatonov.earnit.kids.dto.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.dto.request.ForgotPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginRequest;
import com.sashplatonov.earnit.kids.dto.request.RegisterRequest;
import com.sashplatonov.earnit.kids.dto.request.ResetPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.VerifyEmailRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthConfigResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.CookieBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final int AUTH_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

    private final AuthService authService;
    private final CookieBuilder cookieBuilder;
    private final boolean emailVerificationEnabled;
    private final boolean passwordRecoveryEnabled;

    @Inject
    public AuthResource(AuthService authService,
                        CookieBuilder cookieBuilder,
                        @ConfigProperty(name = "app.email-verification.enabled", defaultValue = "true")
                        boolean emailVerificationEnabled,
                        @ConfigProperty(name = "app.password-recovery.enabled", defaultValue = "true")
                        boolean passwordRecoveryEnabled) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
        this.emailVerificationEnabled = emailVerificationEnabled;
        this.passwordRecoveryEnabled = passwordRecoveryEnabled;
    }

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
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
                    .entity(AuthResponse.failure(f.message()))
                    .build();
        };
    }

    @POST
    @Path("/login-child")
    public Response loginChild(LoginChildRequest request) {
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
                    .entity(AuthResponse.failure(f.message()))
                    .build();
        };
    }

    @POST
    @Path("/logout")
    public Response logout() {
        var cookies = cookieBuilder.buildLogoutCookies();
        Response.ResponseBuilder rb = Response.ok(SimpleResponse.ok());
        cookies.forEach(c -> rb.header("Set-Cookie", c));
        return rb.build();
    }

    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
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
                    .entity(AuthResponse.failure(f.message()))
                    .build();
        };
    }

    @POST
    @Path("/forgot-password")
    public Response forgotPassword(@Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Always return 200 to prevent email enumeration
        return Response.ok(SimpleResponse.ok()).build();
    }

    @POST
    @Path("/change-pin")
    public Response changePin(@Context ContainerRequestContext ctx,
                              Map<String, String> body) {
        AuthContext auth = getAuth(ctx);
        if (auth == null || !auth.isAdmin()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(SimpleResponse.error("Unauthorized"))
                .build();
        }

        String oldPin = body != null ? body.get("oldPin") : null;
        String newPin = body != null ? body.get("newPin") : null;
        OperationResult<Void> result = authService.changeAdminPin(auth.familyId(), oldPin, newPin);

        return switch (result) {
            case OperationResult.Success<Void> _ -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f -> Response.status(Response.Status.BAD_REQUEST)
                .entity(SimpleResponse.error(f.message()))
                .build();
        };
    }

    @POST
    @Path("/reset-password")
    public Response resetPassword(@Valid ResetPasswordRequest request) {
        OperationResult<Void> result = authService.resetPassword(
            request.email(), request.token(), request.password());

        return switch (result) {
            case OperationResult.Success<Void> _ -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(SimpleResponse.error(f.message()))
                    .build();
        };
    }

    @POST
    @Path("/verify")
    public Response verifyEmail(@Valid VerifyEmailRequest request) {
        OperationResult<Void> result = authService.verifyEmail(request.email(), request.token());

        return switch (result) {
            case OperationResult.Success<Void> _ -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(SimpleResponse.error(f.message()))
                    .build();
        };
    }

    @GET
    @Path("/auth-config")
    public Response authConfig() {
        return Response.ok(new AuthConfigResponse(emailVerificationEnabled, passwordRecoveryEnabled))
            .build();
    }

    private AuthContext getAuth(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }

}
