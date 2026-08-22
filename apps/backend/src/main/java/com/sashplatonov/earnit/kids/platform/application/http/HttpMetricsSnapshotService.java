package com.sashplatonov.earnit.kids.platform.application.http;

import com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HttpMetricsSnapshotService {

    private final HttpRequestMetricsRegistry metricsRegistry;

    public HttpMetricsResponse getHttpMetrics() {
        return metricsRegistry.snapshot();
    }
}
