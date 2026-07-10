package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.ApplicationLogsResponse;
import com.sashplatonov.earnit.kids.dto.response.DatabaseHealthResponse;
import com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse;
import com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SystemDashboardService {

    private final SystemOverviewService systemOverviewService;
    private final DatabaseHealthService databaseHealthService;
    private final HttpMetricsSnapshotService httpMetricsSnapshotService;
    private final ApplicationLogService applicationLogService;

    public SystemOverviewResponse getOverview() {
        return systemOverviewService.getOverview();
    }

    public DatabaseHealthResponse getDbHealth() {
        return databaseHealthService.getDbHealth();
    }

    public HttpMetricsResponse getHttpMetrics() {
        return httpMetricsSnapshotService.getHttpMetrics();
    }

    public ApplicationLogsResponse getLogs(String level, int limit) {
        return applicationLogService.getLogs(level, limit);
    }
}
