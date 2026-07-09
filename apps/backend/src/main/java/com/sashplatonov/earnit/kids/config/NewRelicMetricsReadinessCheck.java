package com.sashplatonov.earnit.kids.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class NewRelicMetricsReadinessCheck implements HealthCheck {

    private static final String CHECK_NAME = "new-relic-metrics-config";

    private final AppConfig appConfig;

    @Inject
    public NewRelicMetricsReadinessCheck(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public HealthCheckResponse call() {
        AppConfig.Observability.NewRelic.Metrics metrics = appConfig.observability().newRelic().metrics();
        if (!metrics.enabled()) {
            return HealthCheckResponse.named(CHECK_NAME)
                .up()
                .withData("enabled", false)
                .build();
        }

        if (metrics.licenseKey().isEmpty()) {
            return HealthCheckResponse.named(CHECK_NAME)
                .down()
                .withData("enabled", true)
                .withData("reason", "license-key-missing")
                .build();
        }

        if (metrics.otlpMetricsEndpoint() == null || metrics.otlpMetricsEndpoint().isBlank()) {
            return HealthCheckResponse.named(CHECK_NAME)
                .down()
                .withData("enabled", true)
                .withData("reason", "endpoint-missing")
                .build();
        }

        if (metrics.otlpMetricsProtocol() == null || metrics.otlpMetricsProtocol().isBlank()) {
            return HealthCheckResponse.named(CHECK_NAME)
                .down()
                .withData("enabled", true)
                .withData("reason", "protocol-missing")
                .build();
        }

        return HealthCheckResponse.named(CHECK_NAME)
            .up()
            .withData("enabled", true)
            .build();
    }
}
