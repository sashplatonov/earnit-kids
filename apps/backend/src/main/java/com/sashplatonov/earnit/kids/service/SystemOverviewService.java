package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SystemOverviewService {

    private final TimeProvider timeProvider;

    @CacheResult(cacheName = "system-overview")
    public SystemOverviewResponse getOverview() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();

        Double systemLoad = osBean.getSystemLoadAverage() >= 0 ? osBean.getSystemLoadAverage() : null;
        return new SystemOverviewResponse(
            new SystemOverviewResponse.ProcessStats(
                runtime.totalMemory(),
                memoryMXBean.getHeapMemoryUsage().getUsed(),
                uptimeMillis / 1_000L
            ),
            new SystemOverviewResponse.OperatingSystemStats(
                systemLoad,
                systemLoad,
                systemLoad,
                osBean.getAvailableProcessors()
            ),
            timeProvider.now().toString()
        );
    }
}
