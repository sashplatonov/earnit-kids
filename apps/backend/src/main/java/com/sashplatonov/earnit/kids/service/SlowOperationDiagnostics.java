package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.TraceFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class SlowOperationDiagnostics {

    private static final String REQUEST_KIND = "slow-request";
    private static final String QUERY_KIND = "slow-query";
    private static final String FAILED_KIND = "failed";

    private final AppConfig appConfig;

    public int slowRequestThresholdMs() {
        return appConfig.performance().httpMetrics().slowRequestThresholdMs();
    }

    public int slowQueryThresholdMs() {
        return appConfig.performance().httpMetrics().slowQueryThresholdMs();
    }

    public boolean shouldLogSlowRequest(long durationMs) {
        return durationMs >= slowRequestThresholdMs();
    }

    public boolean shouldLogSlowQuery(long durationMs) {
        return durationMs >= slowQueryThresholdMs();
    }

    public void recordRequest(String method, String path, int status, long durationMs) {
        if (status >= 500) {
            log.error(describeRequest(FAILED_KIND, method, path, status, durationMs));
            return;
        }

        if (shouldLogSlowRequest(durationMs)) {
            log.warn(describeRequest(REQUEST_KIND, method, path, status, durationMs));
        }
    }

    public <T> T recordQuery(String operation, Supplier<T> action, String... details) {
        long startedAt = System.nanoTime();
        try {
            T result = action.get();
            long durationMs = elapsedMs(startedAt);
            if (shouldLogSlowQuery(durationMs)) {
                log.warn(describeQuery(QUERY_KIND, operation, durationMs, details));
            }
            return result;
        } catch (RuntimeException ex) {
            long durationMs = elapsedMs(startedAt);
            log.error(describeQuery(FAILED_KIND, operation, durationMs, details), ex);
            throw ex;
        }
    }

    public String describeRequest(String kind, String method, String path, int status, long durationMs) {
        var fields = baseFields(durationMs);
        fields.put("kind", kind);
        fields.put("method", safe(method, "GET"));
        fields.put("path", safe(path, "/"));
        fields.put("status", String.valueOf(status));
        fields.put("thresholdMs", String.valueOf(slowRequestThresholdMs()));
        return describe(REQUEST_KIND, fields);
    }

    public String describeQuery(String kind, String operation, long durationMs, String... details) {
        var fields = baseFields(durationMs);
        fields.put("kind", kind);
        fields.put("operation", safe(operation, "-"));
        fields.put("thresholdMs", String.valueOf(slowQueryThresholdMs()));
        addDetails(fields, details);
        return describe(QUERY_KIND, fields);
    }

    private Map<String, String> baseFields(long durationMs) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("traceId", mdcOrDefault(TraceFilter.TRACE_ID, "-"));
        fields.put("requestMethod", mdcOrDefault(TraceFilter.REQUEST_METHOD, "-"));
        fields.put("requestPath", mdcOrDefault(TraceFilter.REQUEST_PATH, "-"));
        fields.put("requestQuery", mdcOrDefault(TraceFilter.REQUEST_QUERY, "-"));
        fields.put("role", mdcOrDefault(AuthFilter.MDC_ROLE, "-"));
        fields.put("familyId", mdcOrDefault(AuthFilter.MDC_FAMILY_ID, "-"));
        fields.put("childId", mdcOrDefault(AuthFilter.MDC_CHILD_ID, "-"));
        fields.put("permission", mdcOrDefault(AuthFilter.MDC_PERMISSION, "-"));
        fields.put("durationMs", String.valueOf(Math.max(durationMs, 0)));
        return fields;
    }

    private void addDetails(Map<String, String> fields, String... details) {
        if (details == null || details.length == 0) {
            return;
        }

        for (int index = 0; index < details.length; index += 2) {
            String key = safe(details[index], "detail" + index);
            String value = index + 1 < details.length ? safe(details[index + 1], "-") : "-";
            fields.put(key, value);
        }
    }

    private String describe(String label, Map<String, String> fields) {
        var joiner = new StringJoiner(" ", label + " ", "");
        fields.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }

    private String mdcOrDefault(String key, String fallback) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private long elapsedMs(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
