package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/login-child")
@Tag(name = "Authentication", description = "Child magic-link entrypoints")
public class ChildMagicLinkResource {
    private static final int MAGIC_LINK_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

    private final AuthService authService;
    private final CookieBuilder cookieBuilder;

    @Inject
    public ChildMagicLinkResource(AuthService authService, CookieBuilder cookieBuilder) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
    }

    @GET
    @Path("/{token}")
    @Operation(summary = "Authenticate a child session and redirect to the app")
    @APIResponse(responseCode = "303",
        description = "Redirected to the application root on success or back to login when the token is invalid")
    public Response loginByToken(@Context ContainerRequestContext request,
                                 @Parameter(required = true, description = "Child magic-link token")
                                 @PathParam("token") String token) {
        OperationResult<AuthPayload> result = authService.authenticateChild(token);

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(), payload.childId());

                String forwardedProto = request != null ? request.getHeaderString("X-Forwarded-Proto") : null;
                String forwardedHost = request != null ? request.getHeaderString("X-Forwarded-Host") : null;
                java.net.URI locationUri;
                if (forwardedProto != null && !forwardedProto.isBlank() && forwardedHost != null && !forwardedHost.isBlank()) {
                    String proto = forwardedProto.split(",")[0].trim();
                    String host = forwardedHost.split(",")[0].trim();
                    locationUri = URI.create(proto + ":" + '/' + '/' + host + "/");
                } else {
                    locationUri = URI.create("/");
                }

                Response.ResponseBuilder response = Response.seeOther(locationUri)
                    .header("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0");
                cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
                yield response.build();
            }
            case OperationResult.Failure<AuthPayload> ignored -> Response
                .seeOther(URI.create("/login.html?error=invalid_token"))
                .build();
        };
    }
}
