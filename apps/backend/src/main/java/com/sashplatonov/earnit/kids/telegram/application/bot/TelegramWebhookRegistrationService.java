package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;

import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.function.Supplier;

@ApplicationScoped
public class TelegramWebhookRegistrationService {
    private static final Logger LOG = Logger.getLogger(TelegramWebhookRegistrationService.class);
    private static final String WEBHOOK_PATH = "/api/telegram/webhook";

    private final TelegramFeatureGate featureGate;
    private final TelegramConfig config;
    private final Supplier<TelegramBotApiClient> apiClient;

    @Inject
    public TelegramWebhookRegistrationService(TelegramFeatureGate featureGate,
                                              TelegramConfig config,
                                              TelegramBotApiClient apiClient) {
        this.featureGate = featureGate;
        this.config = config;
        this.apiClient = () -> apiClient;
    }

    void onStart(@Observes StartupEvent ignored) {
        register();
    }

    @Scheduled(every = "{app.telegram.webhook-registration-interval}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        register();
    }

    void register() {
        if (!featureGate.isBotEnabled()) {
            return;
        }
        try {
            URI webhookUrl = webhookUrl(config.miniAppUrl().orElseThrow());
            String secret = config.webhookSecret().orElseThrow();
            registerWithRateLimitRetry(webhookUrl, secret);
            LOG.infof("Telegram webhook registered: %s", webhookUrl);
        } catch (Exception exception) {
            LOG.error("Telegram webhook registration failed", exception);
        }
    }

    private void registerWithRateLimitRetry(URI webhookUrl, String secret) throws Exception {
        try {
            apiClient.get().registerWebhook(webhookUrl, secret);
        } catch (TelegramApiException exception) {
            if (exception.statusCode() != 429 || exception.retryAfterSeconds() <= 0) {
                throw exception;
            }
            LOG.warnf("Telegram webhook registration rate-limited; retrying in %d seconds",
                exception.retryAfterSeconds());
            try {
                Thread.sleep(exception.retryAfterSeconds() * 1_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            apiClient.get().registerWebhook(webhookUrl, secret);
        }
    }

    static URI webhookUrl(String miniAppUrl) {
        URI miniAppUri = URI.create(miniAppUrl);
        if (!"https".equalsIgnoreCase(miniAppUri.getScheme()) || miniAppUri.getHost() == null) {
            throw new IllegalArgumentException("Telegram Mini App URL must be a public HTTPS URL");
        }
        return miniAppUri.resolve(WEBHOOK_PATH);
    }
}
