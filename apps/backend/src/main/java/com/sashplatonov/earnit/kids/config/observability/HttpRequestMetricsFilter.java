package com.sashplatonov.earnit.kids.config.observability;

import com.sashplatonov.earnit.kids.service.http.HttpRequestMetricsRegistry;
import com.sashplatonov.earnit.kids.service.http.HttpResponsePayloadEstimator;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.function.Supplier;

@Provider
@Priority(Priorities.USER)
public class HttpRequestMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_NANOS = "metrics.startNanos";
    private static final String REQUEST_PATH = "metrics.path";
    private final Supplier<HttpRequestMetricsRegistry> metricsRegistry;
    private final Supplier<HttpResponsePayloadEstimator> payloadEstimator;
    private final Supplier<SlowOperationDiagnostics> slowOperationDiagnostics;

    @Inject
    public HttpRequestMetricsFilter(
        HttpRequestMetricsRegistry metricsRegistry,
        HttpResponsePayloadEstimator payloadEstimator,
        SlowOperationDiagnostics slowOperationDiagnostics
    ) {
        this.metricsRegistry = () -> metricsRegistry;
        this.payloadEstimator = () -> payloadEstimator;
        this.slowOperationDiagnostics = () -> slowOperationDiagnostics;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_NANOS, System.nanoTime());
        String path = requestContext.getUriInfo().getPath();
        requestContext.setProperty(REQUEST_PATH, RequestPathNormalizer.normalize(path));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object startValue = requestContext.getProperty(START_NANOS);
        long startNanos = startValue instanceof Long value ? value : System.nanoTime();
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        Object pathValue = requestContext.getProperty(REQUEST_PATH);
        String path = pathValue == null ? "/" : pathValue.toString();

        long payloadBytes = payloadEstimator.get().estimate(requestContext, responseContext, path);

        metricsRegistry.get().record(
            requestContext.getMethod(),
            path,
            responseContext.getStatus(),
            durationMs,
            payloadBytes
        );

        slowOperationDiagnostics.get().recordRequest(
            requestContext.getMethod(),
            path,
            responseContext.getStatus(),
            durationMs
        );
    }

}
