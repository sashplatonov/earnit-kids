package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/login-child")
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
    public Response loginByToken(@PathParam("token") String token) {
        OperationResult<AuthPayload> result = authService.authenticateChild(token);

        return switch (result) {
            case OperationResult.Success<AuthPayload> s -> {
                AuthPayload payload = s.value();
                var cookies = cookieBuilder.buildAuthCookies(
                    payload.email(), payload.role(), payload.familyId(),
                    payload.childId(), MAGIC_LINK_COOKIE_MAX_AGE);

                Response.ResponseBuilder response = Response.seeOther(URI.create("/"))
                    .header("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0");
                cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
                yield response.build();
            }
            case OperationResult.Failure<AuthPayload> _ -> Response
                .seeOther(URI.create("/login.html?error=invalid_token"))
                .build();
        };
    }
}
