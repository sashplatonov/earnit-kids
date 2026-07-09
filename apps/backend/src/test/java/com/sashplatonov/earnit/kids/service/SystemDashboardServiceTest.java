package com.sashplatonov.earnit.kids.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

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

        when(systemOverviewService.getOverview()).thenReturn(Map.of("process", Map.of("uptimeSec", 1L)));
        when(databaseHealthService.getDbHealth()).thenReturn(Map.of("db", Map.of("connected", true)));
        when(httpMetricsSnapshotService.getHttpMetrics()).thenReturn(Map.of("routes", List.of()));
        when(applicationLogService.getLogs("error", 50)).thenReturn(Map.of("logs", List.of()));

        assertThat(service.getOverview()).containsKey("process");
        assertThat(service.getDbHealth()).containsKey("db");
        assertThat(service.getHttpMetrics()).containsKey("routes");
        assertThat(service.getLogs("error", 50)).containsKey("logs");

        verify(systemOverviewService).getOverview();
        verify(databaseHealthService).getDbHealth();
        verify(httpMetricsSnapshotService).getHttpMetrics();
        verify(applicationLogService).getLogs("error", 50);
    }
}
