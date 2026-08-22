package com.sashplatonov.earnit.kids.identity.api.resource.account;

import com.sashplatonov.earnit.kids.identity.api.request.UpdateAccountEmailRequest;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.identity.application.account.AccountService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
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

@Path("/api/account")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource extends ResourceAuthSupport {
    private final AccountService accounts;

    @Inject
    public AccountResource(AccountService accounts) {
        this.accounts = accounts;
    }

    @GET
    public Response connection(@Context ContainerRequestContext context) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return response(accounts.connection(auth.familyId(), auth.email()));
    }

    @POST
    @Path("/email")
    public Response changeEmail(@Context ContainerRequestContext context,
                                @Valid UpdateAccountEmailRequest request) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return voidResponse(accounts.changeEmail(auth.familyId(), auth.email(), request.newEmail()));
    }

    @POST
    @Path("/email/unlink")
    public Response unlinkEmail(@Context ContainerRequestContext context) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return voidResponse(accounts.unlinkEmail(auth.familyId(), auth.email()));
    }

    private <T> Response response(OperationResult<T> result) {
        return OperationResultResponses.toOk(result);
    }

    private Response voidResponse(OperationResult<Void> result) {
        return OperationResultResponses.toVoidOk(result);
    }
}
