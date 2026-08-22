package com.sashplatonov.earnit.kids.platform.application.observability;

import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.observability.TraceFilter;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

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
}
