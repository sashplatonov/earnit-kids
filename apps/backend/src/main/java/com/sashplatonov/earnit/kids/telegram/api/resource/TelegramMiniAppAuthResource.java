package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.dto.response.AuthResponse;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.auth.TelegramMiniAppAuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/api/telegram/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelegramMiniAppAuthResource {
    private static final Logger LOG = Logger.getLogger(TelegramMiniAppAuthResource.class);
    private final TelegramFeatureGate featureGate;
    private final TelegramMiniAppAuthService authService;
    private final CookieBuilder cookieBuilder;

    @Inject
    public TelegramMiniAppAuthResource(TelegramFeatureGate featureGate,
                                       TelegramMiniAppAuthService authService,
                                       CookieBuilder cookieBuilder) {
        this.featureGate = featureGate;
        this.authService = authService;
        this.cookieBuilder = cookieBuilder;
    }

    @POST
    @Path("/exchange")
    public Response exchange(@Valid TelegramInitDataRequest request) {
        if (!featureGate.isEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return switch (authService.authenticate(request.initData(), request.token())) {
            case OperationResult.Success<AuthPayload> success -> {
                AuthPayload payload = success.value();
                LOG.infof("Telegram auth exchange success: role=%s, familyId=%s, permission=%s",
                    payload.role(), payload.familyId(), payload.permission());
                if (featureGate.hasRolloutRestriction() && !featureGate.isMiniAppEnabled(payload.familyId())) {
                    LOG.warnf("Mini app disabled for familyId=%s due to rollout restriction", payload.familyId());
                    yield Response.status(Response.Status.NOT_FOUND).build();
                }
                Response.ResponseBuilder response = Response.ok(
                    payload.childId() == null
                        ? AuthResponse.success(payload.role(), payload.familyId(), payload.locale(), payload.languageSetupRequired())
                        : AuthResponse.childSuccess(payload.familyId(), payload.childId(), payload.childName(),
                            payload.locale(), payload.languageSetupRequired()));
                var cookies = payload.parentAccountId() == null
                    ? cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                        payload.childId(), payload.permission())
                    : cookieBuilder.buildAuthCookies(payload.email(), payload.role(), payload.familyId(),
                        payload.childId(), payload.permission(), payload.parentAccountId());
                cookies.forEach(cookie -> response.header("Set-Cookie", cookie));
                yield response.build();
            }
            case OperationResult.Failure<AuthPayload> failure -> Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.of(failure.message(), failure.errorCode(), 401)).build();
        };
    }

    public record TelegramInitDataRequest(@NotBlank String initData, String token) { }
}
