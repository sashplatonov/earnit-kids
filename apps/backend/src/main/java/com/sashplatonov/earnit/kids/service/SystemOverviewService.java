package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SystemOverviewService {

    private final TimeProvider timeProvider;

    public Map<String, Object> getOverview() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> process = new LinkedHashMap<>();
        process.put("rssBytes", runtime.totalMemory());
        process.put("heapUsedBytes", memoryMXBean.getHeapMemoryUsage().getUsed());
        process.put("uptimeSec", uptimeMillis / 1_000L);

        Double systemLoad = osBean.getSystemLoadAverage() >= 0 ? osBean.getSystemLoadAverage() : null;
        Map<String, Object> os = new LinkedHashMap<>();
        os.put("loadAvg1", systemLoad);
        os.put("loadAvg5", systemLoad);
        os.put("loadAvg15", systemLoad);
        os.put("availableProcessors", osBean.getAvailableProcessors());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("process", process);
        payload.put("os", os);
        payload.put("timestamp", timeProvider.now().toString());
        return payload;
    }
}
