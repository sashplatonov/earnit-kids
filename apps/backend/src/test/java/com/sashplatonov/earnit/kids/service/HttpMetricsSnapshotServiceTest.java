package com.sashplatonov.earnit.kids.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMetricsSnapshotServiceTest {

    @Test
    void getHttpMetrics_returnsRegistrySnapshot() {
        HttpRequestMetricsRegistry registry = new HttpRequestMetricsRegistry();
        registry.record("GET", "/api/test", 200, 18);
        HttpMetricsSnapshotService service = new HttpMetricsSnapshotService(registry);

        Map<String, Object> payload = service.getHttpMetrics();

        assertThat(payload).containsKeys("summary", "topEndpoints");
        assertThat(((Map<?, ?>) payload.get("summary")).get("totalRequests")).isEqualTo(1L);
        assertThat((java.util.List<?>) payload.get("topEndpoints")).hasSize(1);
    }
}
