package com.sashplatonov.earnit.kids.config;

import com.sashplatonov.earnit.kids.service.HttpRequestMetricsRegistry;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;

@Provider
@Priority(Priorities.USER)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HttpRequestMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_NANOS = "metrics.startNanos";
    private static final String REQUEST_PATH = "metrics.path";
    private final HttpRequestMetricsRegistry metricsRegistry;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_NANOS, System.nanoTime());
        String path = requestContext.getUriInfo().getPath();
        requestContext.setProperty(REQUEST_PATH, path == null || path.isBlank() ? "/" : "/" + path);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object startValue = requestContext.getProperty(START_NANOS);
        long startNanos = startValue instanceof Long value ? value : System.nanoTime();
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        Object pathValue = requestContext.getProperty(REQUEST_PATH);
        String path = pathValue == null ? "/" : pathValue.toString();
        metricsRegistry.record(requestContext.getMethod(), path, responseContext.getStatus(), durationMs);
    }
}