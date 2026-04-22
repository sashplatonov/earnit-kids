package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.PublicOriginResolver;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import java.util.Optional;

@Path("/login-child")
@Tag(name = "Authentication", description = "Child magic-link entrypoints")
public class ChildMagicLinkResource {
    private final AuthService authService;
    private final CookieBuilder cookieBuilder;
    private final PublicOriginResolver publicOriginResolver;

    public ChildMagicLinkResource(AuthService authService, CookieBuilder cookieBuilder) {
        this(authService, cookieBuilder, (String) null);
    }

    @Inject
    public ChildMagicLinkResource(AuthService authService,
                                  CookieBuilder cookieBuilder,
                                  @ConfigProperty(name = "APP_URL") Optional<String> appUrl) {
        this(authService, cookieBuilder, appUrl.orElse(null));
    }

    ChildMagicLinkResource(AuthService authService, CookieBuilder cookieBuilder, String appUrl) {
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
        this.publicOriginResolver = new PublicOriginResolver(appUrl);
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

                URI locationUri = URI.create(publicOriginResolver.toAbsoluteRedirect("/", request));

                Response.ResponseBuilder response = Response.seeOther(locationUri)
                    .header("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0");
                cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
                yield response.build();
            }
            case OperationResult.Failure<AuthPayload> ignored -> Response
                .seeOther(URI.create(publicOriginResolver.toAbsoluteRedirect("/login.html?error=invalid_token", request)))
                .build();
        };
    }
}
