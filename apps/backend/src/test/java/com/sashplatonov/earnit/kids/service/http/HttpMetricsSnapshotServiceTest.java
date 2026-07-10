package com.sashplatonov.earnit.kids.service.http;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMetricsSnapshotServiceTest {

    @Test
    void getHttpMetrics_returnsRegistrySnapshot() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpRequestMetricsRegistry registry = new HttpRequestMetricsRegistry(meterRegistry);
        registry.record("GET", "/api/test", 200, 18);
        HttpMetricsSnapshotService service = new HttpMetricsSnapshotService(registry);

        var payload = service.getHttpMetrics();

        assertThat(payload.summary().totalRequests()).isEqualTo(1L);
        assertThat(payload.topEndpoints()).hasSize(1);
        assertThat(meterRegistry.find("earnit.backend.http.request.count").counter()).isNotNull();
    }

    @Test
    void getHttpMetrics_updatesMicrometerMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpRequestMetricsRegistry registry = new HttpRequestMetricsRegistry(meterRegistry);

        registry.record("POST", "/api/super/family/123/data", 503, 47, 1024);

        assertThat(
            meterRegistry.find("earnit.backend.http.request.duration")
                .tags("method", "POST", "route", "/api/super/family/{id}/data", "status", "5xx")
                .timer()
        ).isNotNull();
        assertThat(
            meterRegistry.find("earnit.backend.http.request.errors")
                .tags("method", "POST", "route", "/api/super/family/{id}/data", "status", "5xx")
                .counter()
        ).isNotNull();
        assertThat(
            meterRegistry.find("earnit.backend.http.response.payload.bytes")
                .tags("method", "POST", "route", "/api/super/family/{id}/data")
                .summary()
        ).isNotNull();
    }
}
