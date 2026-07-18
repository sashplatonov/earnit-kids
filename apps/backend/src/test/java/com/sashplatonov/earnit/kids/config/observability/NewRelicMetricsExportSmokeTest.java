package com.sashplatonov.earnit.kids.config.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;

@QuarkusTest
@TestProfile(NewRelicMetricsExportSmokeTest.NewRelicMetricsEnabledProfile.class)
class NewRelicMetricsExportSmokeTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    Config config;

    @Test
    void startup_enablesMicrometerAndOtlpMetrics() {
        assertThat(config.getValue("app.performance.http-metrics.payload-estimation-enabled", Boolean.class))
            .isTrue();
        assertThat(
            config.getValue("app.performance.http-metrics.payload-estimation-max-collection-size", Integer.class)
        ).isEqualTo(256);
        assertThat(config.getValue("app.observability.new-relic.agent-enabled", Boolean.class)).isFalse();
        assertThat(config.getValue("app.observability.new-relic.metrics.enabled", Boolean.class)).isTrue();
        assertThat(config.getValue("app.observability.new-relic.metrics.otlp-metrics-endpoint", String.class))
            .isEqualTo("http://127.0.0.1:4318");
        assertThat(config.getValue("app.observability.new-relic.metrics.otlp-metrics-protocol", String.class))
            .isEqualTo("http/protobuf");
        assertThat(config.getValue("app.observability.new-relic.metrics.license-key", String.class))
            .isEqualTo("test-license");
        assertThat(config.getValue("app.observability.new-relic.logging.forwarding-enabled", Boolean.class))
            .isFalse();
        assertThat(
            config.getValue(
                "app.observability.new-relic.logging.forwarding-max-samples-stored",
                Integer.class
            )
        ).isEqualTo(10000);
        assertThat(config.getValue("app.observability.new-relic.logging.local-decorating-enabled", Boolean.class))
            .isFalse();
        assertThat(config.getValue("quarkus.otel.metrics.enabled", Boolean.class)).isTrue();
        assertThat(config.getValue("quarkus.otel.exporter.otlp.metrics.endpoint", String.class))
            .isEqualTo("http://127.0.0.1:4318");
        assertThat(config.getValue("quarkus.otel.traces.exporter", String.class)).isEqualTo("none");
        assertThat(config.getValue("quarkus.otel.logs.exporter", String.class)).isEqualTo("none");
        assertThat(config.getValue("quarkus.otel.logs.handler.enabled", Boolean.class)).isFalse();
        assertThat(config.getValue("quarkus.micrometer.binder.jvm", Boolean.class)).isTrue();
        assertThat(config.getValue("quarkus.micrometer.binder.http-server.enabled", Boolean.class)).isTrue();
        assertThat(meterRegistry.find("jvm.memory.used").meter()).isNotNull();
    }

    public static class NewRelicMetricsEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                entry("app.performance.http-metrics.payload-estimation-enabled", "true"),
                entry("app.performance.http-metrics.payload-estimation-max-collection-size", "256"),
                entry("app.observability.new-relic.agent-enabled", "false"),
                entry("app.observability.new-relic.metrics.enabled", "true"),
                entry("app.observability.new-relic.metrics.otlp-metrics-endpoint", "http://127.0.0.1:4318"),
                entry("app.observability.new-relic.metrics.otlp-metrics-protocol", "http/protobuf"),
                entry("app.observability.new-relic.metrics.license-key", "test-license"),
                entry("app.observability.new-relic.logging.forwarding-enabled", "false"),
                entry("app.observability.new-relic.logging.forwarding-max-samples-stored", "10000"),
                entry("app.observability.new-relic.logging.local-decorating-enabled", "false"),
                entry("quarkus.otel.metric.export.interval", "24h")
            );
        }
    }
}
