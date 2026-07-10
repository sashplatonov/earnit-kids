package com.sashplatonov.earnit.kids.service.observability;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.observability.TraceFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Duration;
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
        StringBuilder builder = baseBuilder(REQUEST_KIND);
        appendField(builder, "kind", kind);
        appendField(builder, "traceId", mdcOrDefault(TraceFilter.TRACE_ID, "-"));
        appendField(builder, "requestMethod", mdcOrDefault(TraceFilter.REQUEST_METHOD, "-"));
        appendField(builder, "requestPath", mdcOrDefault(TraceFilter.REQUEST_PATH, "-"));
        appendField(builder, "requestQuery", mdcOrDefault(TraceFilter.REQUEST_QUERY, "-"));
        appendField(builder, "role", mdcOrDefault(AuthFilter.MDC_ROLE, "-"));
        appendField(builder, "familyId", mdcOrDefault(AuthFilter.MDC_FAMILY_ID, "-"));
        appendField(builder, "childId", mdcOrDefault(AuthFilter.MDC_CHILD_ID, "-"));
        appendField(builder, "permission", mdcOrDefault(AuthFilter.MDC_PERMISSION, "-"));
        appendField(builder, "durationMs", String.valueOf(Math.max(durationMs, 0)));
        appendField(builder, "method", safe(method, "GET"));
        appendField(builder, "path", safe(path, "/"));
        appendField(builder, "status", String.valueOf(status));
        appendField(builder, "thresholdMs", String.valueOf(slowRequestThresholdMs()));
        return stripTrailingSpace(builder);
    }

    public String describeQuery(String kind, String operation, long durationMs, String... details) {
        return describeQueryLike(kind, operation, durationMs, details);
    }

    private String describeQueryLike(String kind, String operation, long durationMs, String... details) {
        StringBuilder builder = baseBuilder(QUERY_KIND);
        appendField(builder, "kind", kind);
        appendField(builder, "operation", safe(operation, "-"));
        appendField(builder, "traceId", mdcOrDefault(TraceFilter.TRACE_ID, "-"));
        appendField(builder, "requestMethod", mdcOrDefault(TraceFilter.REQUEST_METHOD, "-"));
        appendField(builder, "requestPath", mdcOrDefault(TraceFilter.REQUEST_PATH, "-"));
        appendField(builder, "requestQuery", mdcOrDefault(TraceFilter.REQUEST_QUERY, "-"));
        appendField(builder, "role", mdcOrDefault(AuthFilter.MDC_ROLE, "-"));
        appendField(builder, "familyId", mdcOrDefault(AuthFilter.MDC_FAMILY_ID, "-"));
        appendField(builder, "childId", mdcOrDefault(AuthFilter.MDC_CHILD_ID, "-"));
        appendField(builder, "permission", mdcOrDefault(AuthFilter.MDC_PERMISSION, "-"));
        appendField(builder, "durationMs", String.valueOf(Math.max(durationMs, 0)));
        appendField(builder, "thresholdMs", String.valueOf(slowQueryThresholdMs()));
        if (details == null || details.length == 0) {
            return stripTrailingSpace(builder);
        }

        for (int index = 0; index < details.length; index += 2) {
            String key = safe(details[index], "detail" + index);
            String value = index + 1 < details.length ? safe(details[index + 1], "-") : "-";
            appendField(builder, key, value);
        }
        return stripTrailingSpace(builder);
    }

    private StringBuilder baseBuilder(String label) {
        StringBuilder builder = new StringBuilder(192);
        builder.append(label).append(' ');
        return builder;
    }

    private void appendField(StringBuilder builder, String key, String value) {
        builder.append(key).append('=').append(value).append(' ');
    }

    private String stripTrailingSpace(StringBuilder builder) {
        int length = builder.length();
        return length > 0 && builder.charAt(length - 1) == ' '
            ? builder.substring(0, length - 1)
            : builder.toString();
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
