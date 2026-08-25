package com.sashplatonov.earnit.kids.platform.application.observability;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.observability.TraceFilter;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlowOperationDiagnosticsTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void thresholds_areLoadedFromConfig() {
        SlowOperationDiagnostics diagnostics = new SlowOperationDiagnostics(
            TestConfigFactory.appConfig(false, null, false, false)
        );

        assertThat(diagnostics.slowRequestThresholdMs()).isEqualTo(750);
        assertThat(diagnostics.slowQueryThresholdMs()).isEqualTo(250);
        assertThat(diagnostics.shouldLogSlowRequest(749)).isFalse();
        assertThat(diagnostics.shouldLogSlowRequest(750)).isTrue();
        assertThat(diagnostics.shouldLogSlowQuery(249)).isFalse();
        assertThat(diagnostics.shouldLogSlowQuery(250)).isTrue();
    }

    @Test
    void describeRequest_includesTraceAndScopeContext() {
        MDC.put(TraceFilter.TRACE_ID, "trace-123");
        MDC.put(TraceFilter.REQUEST_METHOD, "POST");
        MDC.put(TraceFilter.REQUEST_PATH, "/api/family");
        MDC.put(TraceFilter.REQUEST_QUERY, "childId=10");
        MDC.put(AuthFilter.MDC_FAMILY_ID, "fam-1");
        MDC.put(AuthFilter.MDC_CHILD_ID, "10");
        MDC.put(AuthFilter.MDC_ROLE, "admin");
        MDC.put(AuthFilter.MDC_PERMISSION, "family:read");

        SlowOperationDiagnostics diagnostics = new SlowOperationDiagnostics(
            TestConfigFactory.appConfig(false, null, false, false)
        );

        String message = diagnostics.describeRequest("slow", "POST", "/api/family", 200, 851);

        assertThat(message).contains("slow-request");
        assertThat(message).contains("kind=slow");
        assertThat(message).contains("traceId=trace-123");
        assertThat(message).contains("requestPath=/api/family");
        assertThat(message).contains("requestQuery=childId=10");
        assertThat(message).contains("familyId=fam-1");
        assertThat(message).contains("childId=10");
        assertThat(message).contains("durationMs=851");
        assertThat(message).contains("thresholdMs=750");
    }

    @Test
    void describeQuery_includesOperationAndScopeDetails() {
        MDC.put(TraceFilter.TRACE_ID, "trace-123");
        MDC.put(TraceFilter.REQUEST_PATH, "/api/family/analytics");
        MDC.put(AuthFilter.MDC_FAMILY_ID, "fam-1");
        MDC.put(AuthFilter.MDC_CHILD_ID, "10");

        SlowOperationDiagnostics diagnostics = new SlowOperationDiagnostics(
            TestConfigFactory.appConfig(false, null, false, false)
        );

        String message = diagnostics.describeQuery(
            "slow",
            "family-data.getTasks",
            312,
            "familyDbId",
            "42",
            "childId",
            "10"
        );

        assertThat(message).contains("slow-query");
        assertThat(message).contains("kind=slow");
        assertThat(message).contains("operation=family-data.getTasks");
        assertThat(message).contains("traceId=trace-123");
        assertThat(message).contains("requestPath=/api/family/analytics");
        assertThat(message).contains("familyId=fam-1");
        assertThat(message).contains("familyDbId=42");
        assertThat(message).contains("childId=10");
        assertThat(message).contains("durationMs=312");
        assertThat(message).contains("thresholdMs=250");
    }

    @Test
    void recordRequest_logsServerErrorsAndSlowSuccessfulRequests() {
        SlowOperationDiagnostics diagnostics = diagnosticsWithThresholds(0, 0);

        diagnostics.recordRequest("GET", "/health", 500, 1);
        diagnostics.recordRequest("GET", "/health", 200, 1);
    }

    @Test
    void recordQuery_returnsSuccessfulResultAndRethrowsFailures() {
        SlowOperationDiagnostics diagnostics = diagnosticsWithThresholds(0, 0);

        assertThat(diagnostics.recordQuery("health.check", () -> "ok", "attempt", "1"))
            .isEqualTo("ok");
        assertThatThrownBy(() -> diagnostics.recordQuery(
            "health.check", () -> { throw new IllegalStateException("database unavailable"); },
            "attempt"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database unavailable");
    }

    @Test
    void descriptions_useSafeFallbacksForMissingContextAndDetails() {
        SlowOperationDiagnostics diagnostics = diagnosticsWithThresholds(750, 250);

        String request = diagnostics.describeRequest(null, null, null, 200, -1);
        String query = diagnostics.describeQuery(null, null, -1, null, "value", "odd");

        assertThat(request)
            .contains("requestMethod=-")
            .contains("requestPath=-")
            .contains("method=GET")
            .contains("path=/")
            .contains("durationMs=0");
        assertThat(query)
            .contains("operation=-")
            .contains("durationMs=0")
            .contains("detail0=value")
            .contains("odd=-");
    }

    private static SlowOperationDiagnostics diagnosticsWithThresholds(
        int slowRequestThresholdMs, int slowQueryThresholdMs) {
        AppConfig config = mock(AppConfig.class);
        AppConfig.Performance performance = mock(AppConfig.Performance.class);
        AppConfig.Performance.HttpMetrics httpMetrics = mock(AppConfig.Performance.HttpMetrics.class);
        when(config.performance()).thenReturn(performance);
        when(performance.httpMetrics()).thenReturn(httpMetrics);
        when(httpMetrics.slowRequestThresholdMs()).thenReturn(slowRequestThresholdMs);
        when(httpMetrics.slowQueryThresholdMs()).thenReturn(slowQueryThresholdMs);
        return new SlowOperationDiagnostics(config);
    }
}
