package com.sashplatonov.earnit.kids.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemDashboardServiceTest {

    @Mock SystemOverviewService systemOverviewService;
    @Mock DatabaseHealthService databaseHealthService;
    @Mock HttpMetricsSnapshotService httpMetricsSnapshotService;
    @Mock ApplicationLogService applicationLogService;

    @Test
    void delegatesOverviewDbMetricsAndLogsToSpecializedServices() {
        SystemDashboardService service = new SystemDashboardService(
            systemOverviewService,
            databaseHealthService,
            httpMetricsSnapshotService,
            applicationLogService
        );

        when(systemOverviewService.getOverview()).thenReturn(new com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse(
            new com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse.ProcessStats(1L, 2L, 3L),
            new com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse.OperatingSystemStats(0.5, 0.6, 0.7, 8),
            "2026-07-09T00:00:00Z"
        ));
        when(databaseHealthService.getDbHealth()).thenReturn(new com.sashplatonov.earnit.kids.dto.response.DatabaseHealthResponse(
            new com.sashplatonov.earnit.kids.dto.response.DatabaseHealthResponse.DbHealth(true, 12L, null)
        ));
        when(httpMetricsSnapshotService.getHttpMetrics()).thenReturn(new com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse(
            new com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse.HttpMetricsSummary(1L, 0L, 0.0, 5L),
            List.of()
        ));
        when(applicationLogService.getLogs("error", 50)).thenReturn(new com.sashplatonov.earnit.kids.dto.response.ApplicationLogsResponse(List.of()));

        assertThat(service.getOverview().process().uptimeSec()).isEqualTo(3L);
        assertThat(service.getDbHealth().db().connected()).isTrue();
        assertThat(service.getHttpMetrics().summary().totalRequests()).isEqualTo(1L);
        assertThat(service.getLogs("error", 50).logs()).isEmpty();

        verify(systemOverviewService).getOverview();
        verify(databaseHealthService).getDbHealth();
        verify(httpMetricsSnapshotService).getHttpMetrics();
        verify(applicationLogService).getLogs("error", 50);
    }
}
