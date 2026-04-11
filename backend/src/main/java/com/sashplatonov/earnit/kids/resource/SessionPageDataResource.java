package com.sashplatonov.earnit.kids.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import com.sashplatonov.earnit.kids.config.JwtCompatVerifier;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;

@Path("/api/page-data")
@Produces(MediaType.APPLICATION_JSON)
public class SessionPageDataResource {
    private final JwtCompatVerifier jwtCompatVerifier;

    @Inject
    public SessionPageDataResource(JwtCompatVerifier jwtCompatVerifier) {
        this.jwtCompatVerifier = jwtCompatVerifier;
    }

    @GET
    @Path("/session")
    public SessionPageDataResponse session(@HeaderParam("Cookie") String cookieHeader) {
        return jwtCompatVerifier.readSession(cookieHeader);
    }
}