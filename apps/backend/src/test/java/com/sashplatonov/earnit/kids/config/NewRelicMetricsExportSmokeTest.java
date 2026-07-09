package com.sashplatonov.earnit.kids.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(NewRelicMetricsExportSmokeTest.NewRelicMetricsEnabledProfile.class)
class NewRelicMetricsExportSmokeTest {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    Config config;

    @Test
    void startup_enablesMicrometerAndOtlpMetrics() {
        assertThat(config.getValue("quarkus.otel.metrics.enabled", Boolean.class)).isTrue();
        assertThat(config.getValue("quarkus.otel.exporter.otlp.metrics.endpoint", String.class))
            .isEqualTo("http://127.0.0.1:4318");
        assertThat(meterRegistry.find("jvm.memory.used").meter()).isNotNull();
    }

    public static class NewRelicMetricsEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.otel.metrics.enabled", "true",
                "quarkus.otel.exporter.otlp.metrics.endpoint", "http://127.0.0.1:4318",
                "quarkus.otel.exporter.otlp.metrics.protocol", "http/protobuf",
                "quarkus.otel.exporter.otlp.metrics.headers", "api-key=test-license",
                "quarkus.otel.traces.exporter", "none",
                "quarkus.otel.logs.exporter", "none",
                "quarkus.otel.logs.handler.enabled", "false",
                "quarkus.otel.metric.export.interval", "24h"
            );
        }
    }
}
