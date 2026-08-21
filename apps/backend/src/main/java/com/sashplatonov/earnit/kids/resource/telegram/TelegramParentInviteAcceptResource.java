package com.sashplatonov.earnit.kids.resource.telegram;

import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.request.ParentInviteAcceptRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthResponse;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.service.telegram.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.service.telegram.TelegramIdentityService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramParentInvitationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.function.Supplier;

@Path("/api/telegram/parents/invite/accept")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelegramParentInviteAcceptResource {
    private final TelegramParentInvitationService invitations;
    private final TelegramFeatureGate featureGate;
    private final CookieBuilder cookieBuilder;
    private final Supplier<FamilyRepository> families;
    private final TimeProvider timeProvider;

    @Inject
    public TelegramParentInviteAcceptResource(TelegramParentInvitationService invitations,
                                              TelegramFeatureGate featureGate,
                                              CookieBuilder cookieBuilder,
                                              FamilyRepository families,
                                              TimeProvider timeProvider) {
        this.invitations = invitations;
        this.featureGate = featureGate;
        this.cookieBuilder = cookieBuilder;
        this.families = () -> families;
        this.timeProvider = timeProvider;
    }


    @POST
    public Response accept(@Valid ParentInviteAcceptRequest request) {
        if (!featureGate.isEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return switch (invitations.accept(request.token(), request.initData(), null, timeProvider.now())) {
            case OperationResult.Success<TelegramIdentityService.TelegramIdentity> success -> {
                var identity = success.value();
                String familyId = families.get().findFamilyIdByDbId(identity.familyId()).orElse("family-" + identity.familyId());
                Response.ResponseBuilder response = Response.ok(AuthResponse.success("admin", familyId));
                var cookies = identity.parentAccountId() == null
                    ? cookieBuilder.buildAuthCookies(request.legacyEmail(), "admin", familyId, null, false, "editor")
                    : cookieBuilder.buildAuthCookies(null, "admin", familyId, null, false, "editor",
                        identity.parentAccountId());
                cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
                yield response.build();
            }
            case OperationResult.Failure<TelegramIdentityService.TelegramIdentity> failure ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(failure.message(), failure.errorCode(), 400))
                    .build();
        };
    }
}
