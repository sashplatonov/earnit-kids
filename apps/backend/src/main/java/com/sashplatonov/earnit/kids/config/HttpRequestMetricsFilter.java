package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.service.HttpRequestMetricsRegistry;
import com.sashplatonov.earnit.kids.service.SlowOperationDiagnostics;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@Provider
@Priority(Priorities.USER)
@Slf4j
public class HttpRequestMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_NANOS = "metrics.startNanos";
    private static final String REQUEST_PATH = "metrics.path";
    private final HttpRequestMetricsRegistry metricsRegistry;
    private final ObjectMapper objectMapper;
    private final SlowOperationDiagnostics slowOperationDiagnostics;
    private final boolean payloadEstimationEnabled;
    private final int maxCollectionSize;

    @Inject
    public HttpRequestMetricsFilter(
        HttpRequestMetricsRegistry metricsRegistry,
        ObjectMapper objectMapper,
        SlowOperationDiagnostics slowOperationDiagnostics,
        @ConfigProperty(name = "app.performance.http-metrics.payload-estimation-enabled", defaultValue = "true")
        boolean payloadEstimationEnabled,
        @ConfigProperty(name = "app.performance.http-metrics.payload-estimation-max-collection-size", defaultValue = "256")
        int maxCollectionSize
    ) {
        this.metricsRegistry = metricsRegistry;
        this.objectMapper = objectMapper;
        this.slowOperationDiagnostics = slowOperationDiagnostics;
        this.payloadEstimationEnabled = payloadEstimationEnabled;
        this.maxCollectionSize = maxCollectionSize;
    }

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

        long payloadBytes = estimatePayloadBytes(requestContext, responseContext);

        metricsRegistry.record(
            requestContext.getMethod(),
            path,
            responseContext.getStatus(),
            durationMs,
            payloadBytes
        );

        slowOperationDiagnostics.recordRequest(
            requestContext.getMethod(),
            path,
            responseContext.getStatus(),
            durationMs
        );
    }

    private long estimatePayloadBytes(
        ContainerRequestContext requestContext,
        ContainerResponseContext responseContext
    ) {
        Object entity = responseContext.getEntity();
        if (entity == null) {
            return -1;
        }

        Long contentLength = parseContentLength(responseContext);
        if (contentLength != null) {
            return contentLength;
        }

        if (entity instanceof byte[] bytes) {
            return bytes.length;
        }
        if (entity instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }
        if (entity instanceof InputStream) {
            return -1;
        }

        if (shouldSkipEstimation(responseContext.getMediaType(), entity)) {
            return -1;
        }

        try {
            return objectMapper.writeValueAsBytes(entity).length;
        } catch (Exception ex) {
            log.warn(
                "Failed to estimate payload size for {} {}",
                requestContext.getMethod(),
                requestContext.getProperty(REQUEST_PATH),
                ex
            );
            return -1;
        }
    }

    private Long parseContentLength(ContainerResponseContext responseContext) {
        Object headerValue = responseContext.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (headerValue instanceof Number number) {
            return number.longValue();
        }
        if (headerValue instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean shouldSkipEstimation(MediaType mediaType, Object entity) {
        if (!payloadEstimationEnabled) {
            return true;
        }
        if (isBinaryMediaType(mediaType)) {
            return true;
        }
        if (
            entity instanceof Collection<?> collection
                && collection.size() > maxCollectionSize
        ) {
            return true;
        }
        if (entity instanceof Map<?, ?> map && map.size() > maxCollectionSize) {
            return true;
        }
        return entity.getClass().isArray()
            && Array.getLength(entity) > maxCollectionSize;
    }

    private boolean isBinaryMediaType(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        String type = mediaType.getType().toLowerCase(Locale.ROOT);
        if ("text".equals(type)) {
            return false;
        }
        if (!"application".equals(type)) {
            return true;
        }
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
        return !(subtype.contains("json") || subtype.contains("xml") || subtype.contains("text"));
    }
}
