package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HttpMetricsSnapshotService {

    private final HttpRequestMetricsRegistry metricsRegistry;

    public Map<String, Object> getHttpMetrics() {
        return metricsRegistry.snapshot();
    }
}
