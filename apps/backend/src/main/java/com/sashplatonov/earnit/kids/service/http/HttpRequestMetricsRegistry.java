package com.sashplatonov.earnit.kids.service.http;

import com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HttpRequestMetricsRegistry {
    private static final String HTTP_REQUEST_DURATION_METRIC = "earnit.backend.http.request.duration";
    private static final String HTTP_REQUEST_COUNT_METRIC = "earnit.backend.http.request.count";
    private static final String HTTP_REQUEST_ERROR_METRIC = "earnit.backend.http.request.errors";
    private static final String HTTP_RESPONSE_PAYLOAD_METRIC = "earnit.backend.http.response.payload.bytes";
    private static final String TAG_METHOD = "method";
    private static final String TAG_ROUTE = "route";
    private static final String TAG_STATUS = "status";

    private final ConcurrentMap<String, EndpointMetrics> endpoints = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public void record(String method, String path, int status, long durationMs) {
        record(method, path, status, durationMs, -1);
    }

    public void record(String method, String path, int status, long durationMs, long payloadBytes) {
        String safeMethod = method == null || method.isBlank() ? "GET" : method.toUpperCase();
        String safePath = path == null || path.isBlank() ? "/" : path;
        endpoints.computeIfAbsent(safeMethod + " " + safePath, key -> new EndpointMetrics(safeMethod, safePath))
            .record(status, durationMs, payloadBytes);
        recordMicrometerMetrics(safeMethod, safePath, status, durationMs, payloadBytes);
    }

    public HttpMetricsResponse snapshot() {
        List<HttpMetricsResponse.HttpEndpointMetrics> topEndpoints = new ArrayList<>();
        long totalRequests = 0;
        long errorsTotal = 0;
        long totalDuration = 0;

        for (EndpointMetrics metrics : endpoints.values()) {
            long count = metrics.count.sum();
            long errors = metrics.errors.sum();
            long duration = metrics.totalDurationMs.sum();
            totalRequests += count;
            errorsTotal += errors;
            totalDuration += duration;

            long avgDurationMs = count == 0 ? 0 : Math.round((double) duration / count);
            long totalBytes = metrics.totalPayloadBytes.sum();
            long avgPayloadBytes = count == 0 ? 0 : totalBytes / count;
            double payloadMb = count == 0 ? 0 : Math.round((double) totalBytes / 1_048_576.0 * 100) / 100.0;
            topEndpoints.add(new HttpMetricsResponse.HttpEndpointMetrics(
                metrics.method,
                metrics.path,
                count,
                errors,
                avgDurationMs,
                metrics.maxDurationMs.get(),
                avgPayloadBytes,
                metrics.maxPayloadBytes.get(),
                payloadMb
            ));
        }

        topEndpoints.sort(Comparator
            .comparingLong(HttpMetricsResponse.HttpEndpointMetrics::count)
            .reversed()
            .thenComparing(HttpMetricsResponse.HttpEndpointMetrics::path));

        return new HttpMetricsResponse(
            new HttpMetricsResponse.HttpMetricsSummary(
                totalRequests,
                errorsTotal,
                totalRequests == 0 ? 0 : round(((double) errorsTotal / totalRequests) * 100),
                totalRequests == 0 ? 0 : Math.round((double) totalDuration / totalRequests)
            ),
            topEndpoints
        );
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
            .setScale(1, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private void recordMicrometerMetrics(String method, String path, int status, long durationMs, long payloadBytes) {
        String route = normalizeRoute(path);
        String statusClass = status >= 100 ? (status / 100) + "xx" : "unknown";

        Timer.builder(HTTP_REQUEST_DURATION_METRIC)
            .tag(TAG_METHOD, method)
            .tag(TAG_ROUTE, route)
            .tag(TAG_STATUS, statusClass)
            .register(meterRegistry)
            .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);

        meterRegistry.counter(HTTP_REQUEST_COUNT_METRIC, TAG_METHOD, method, TAG_ROUTE, route, TAG_STATUS, statusClass)
            .increment();

        if (status >= 400) {
            meterRegistry.counter(HTTP_REQUEST_ERROR_METRIC, TAG_METHOD, method, TAG_ROUTE, route, TAG_STATUS, statusClass)
                .increment();
        }

        if (payloadBytes >= 0) {
            DistributionSummary.builder(HTTP_RESPONSE_PAYLOAD_METRIC)
                .tag(TAG_METHOD, method)
                .tag(TAG_ROUTE, route)
                .register(meterRegistry)
                .record(payloadBytes);
        }
    }

    private String normalizeRoute(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        StringBuilder builder = new StringBuilder(path.length());
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            builder.append('/');
            builder.append(isHighCardinalitySegment(segment) ? "{id}" : segment);
        }
        return builder.length() == 0 ? "/" : builder.toString();
    }

    private boolean isHighCardinalitySegment(String segment) {
        if (segment.length() >= 24 || isAllDigits(segment)) {
            return true;
        }
        boolean hasDigit = false;
        boolean hasLetter = false;
        boolean hasDash = false;
        for (int index = 0; index < segment.length(); index++) {
            char value = segment.charAt(index);
            hasDigit = hasDigit || Character.isDigit(value);
            hasLetter = hasLetter || Character.isLetter(value);
            hasDash = hasDash || value == '-';
        }
        return (hasDigit && hasLetter) || (hasDash && segment.length() >= 8);
    }

    private boolean isAllDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private static final class EndpointMetrics {
        private final String method;
        private final String path;
        private final LongAdder count = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalDurationMs = new LongAdder();
        private final AtomicLong maxDurationMs = new AtomicLong();
        private final LongAdder totalPayloadBytes = new LongAdder();
        private final AtomicLong maxPayloadBytes = new AtomicLong();

        private EndpointMetrics(String method, String path) {
            this.method = method;
            this.path = path;
        }

        private void record(int status, long durationMs, long payloadBytes) {
            count.increment();
            totalDurationMs.add(Math.max(durationMs, 0));
            if (status >= 400) {
                errors.increment();
            }
            maxDurationMs.accumulateAndGet(Math.max(durationMs, 0), Math::max);
            if (payloadBytes >= 0) {
                totalPayloadBytes.add(payloadBytes);
                maxPayloadBytes.accumulateAndGet(payloadBytes, Math::max);
            }
        }
    }
}
