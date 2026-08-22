package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.telegram.application.bot.TelegramBotService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.notification.TelegramObservability;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Path("/api/telegram/webhook")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TelegramWebhookResource {
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private final TelegramFeatureGate featureGate;
    private final TelegramConfig config;
    private final TelegramBotService botService;
    private final TelegramObservability observability;

    @Inject
    public TelegramWebhookResource(TelegramFeatureGate featureGate,
                                   TelegramConfig config,
                                   TelegramBotService botService,
                                   TelegramObservability observability) {
        this.featureGate = featureGate;
        this.config = config;
        this.botService = botService;
        this.observability = observability;
    }

    public TelegramWebhookResource(TelegramFeatureGate featureGate,
                                   TelegramConfig config,
                                   TelegramBotService botService) {
        this(featureGate, config, botService, null);
    }

    @POST
    public Response receive(@HeaderParam(SECRET_HEADER) String secret, JsonNode update) {
        if (!featureGate.isBotEnabled()) {
            if (observability != null) {
                observability.webhookRejected();
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String expected = config.webhookSecret().orElse("");
        if (secret == null || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            if (observability != null) {
                observability.webhookRejected();
            }
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        botService.handleUpdate(update);
        if (observability != null) {
            observability.webhookAccepted();
        }
        return Response.ok().build();
    }
}
