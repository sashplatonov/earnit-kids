package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SystemDashboardService {

    private final SystemOverviewService systemOverviewService;
    private final DatabaseHealthService databaseHealthService;
    private final HttpMetricsSnapshotService httpMetricsSnapshotService;
    private final ApplicationLogService applicationLogService;

    public Map<String, Object> getOverview() {
        return systemOverviewService.getOverview();
    }

    public Map<String, Object> getDbHealth() {
        return databaseHealthService.getDbHealth();
    }

    public Map<String, Object> getHttpMetrics() {
        return httpMetricsSnapshotService.getHttpMetrics();
    }

    public Map<String, Object> getLogs(String level, int limit) {
        return applicationLogService.getLogs(level, limit);
    }
}
