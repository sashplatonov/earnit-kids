package com.sashplatonov.earnit.kids.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMetricsSnapshotServiceTest {

    @Test
    void getHttpMetrics_returnsRegistrySnapshot() {
        HttpRequestMetricsRegistry registry = new HttpRequestMetricsRegistry();
        registry.record("GET", "/api/test", 200, 18);
        HttpMetricsSnapshotService service = new HttpMetricsSnapshotService(registry);

        var payload = service.getHttpMetrics();

        assertThat(payload.summary().totalRequests()).isEqualTo(1L);
        assertThat(payload.topEndpoints()).hasSize(1);
    }
}
