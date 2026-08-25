package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.sashplatonov.earnit.kids.config.auth.JwtCompatVerifier;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/page-data")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Session", description = "Derived session snapshot endpoints")
public class SessionPageDataResource {

    private static final Logger LOG = Logger.getLogger(SessionPageDataResource.class);
    private final JwtCompatVerifier jwtCompatVerifier;

    @Inject
    FamilyRepository familyRepository;

    @Inject
    public SessionPageDataResource(JwtCompatVerifier jwtCompatVerifier) {
        this.jwtCompatVerifier = jwtCompatVerifier;
    }

    @GET
    @Path("/session")
    @Operation(summary = "Derive a session snapshot from the compatibility auth cookie")
    @APIResponse(responseCode = "200", description = "Session snapshot returned",
        content = @Content(schema = @Schema(implementation = SessionPageDataResponse.class)))
    public Response session(@Parameter(description = "Incoming Cookie header")
                                           @HeaderParam("Cookie") String cookieHeader) {
        var resp = jwtCompatVerifier.readSession(cookieHeader);
        LOG.debugf("Session snapshot: authenticated=%s, role=%s, familyId=%s",
            resp.authenticated(), resp.role(), resp.familyId());
        if (!resp.authenticated() || familyRepository == null) {
            return Response.ok(resp).build();
        }
        return familyRepository.findById(resp.familyId())
            .map(family -> Response.ok(new SessionPageDataResponse(
                true,
                resp.role(),
                resp.familyId(),
                resp.childId(),
                resp.email(),
                resp.csrfToken(),
                resp.permission(),
                family.getLocale() == null ? "en" : family.getLocale(),
                family.getLocale() == null && "family_admin".equals(resp.permission())
            )).build())
            .orElseGet(() -> Response.ok(resp).build());
    }
}
