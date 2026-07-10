package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@ApplicationScoped
public class HttpRequestMetricsRegistry {

    private final ConcurrentMap<String, EndpointMetrics> endpoints = new ConcurrentHashMap<>();

    public void record(String method, String path, int status, long durationMs) {
        record(method, path, status, durationMs, -1);
    }

    public void record(String method, String path, int status, long durationMs, long payloadBytes) {
        String safeMethod = method == null || method.isBlank() ? "GET" : method.toUpperCase();
        String safePath = path == null || path.isBlank() ? "/" : path;
        endpoints.computeIfAbsent(safeMethod + " " + safePath, key -> new EndpointMetrics(safeMethod, safePath))
            .record(status, durationMs, payloadBytes);
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
