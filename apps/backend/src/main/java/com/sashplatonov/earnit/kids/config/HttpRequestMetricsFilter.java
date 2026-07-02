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
import lombok.extern.slf4j.Slf4j;

@Provider
@Priority(Priorities.USER)
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
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

        // EXPLAIN: Estimate payload size from serialized entity bytes
        long payloadBytes = -1;
        var entity = responseContext.getEntity();
        if (entity instanceof byte[] bytes) {
            payloadBytes = bytes.length;
        } else if (entity instanceof String text) {
            payloadBytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } else if (entity != null) {
            // EXPLAIN: Approximate serialized JSON size — rough but useful for trend tracking
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                byte[] json = mapper.writeValueAsBytes(entity);
                payloadBytes = json.length;
            } catch (Exception ex) {
                log.warn("Failed to estimate payload size for {} {}", requestContext.getMethod(), path, ex);
                payloadBytes = -1;
            }
        }

        metricsRegistry.record(requestContext.getMethod(), path, responseContext.getStatus(), durationMs, payloadBytes);
    }
}
