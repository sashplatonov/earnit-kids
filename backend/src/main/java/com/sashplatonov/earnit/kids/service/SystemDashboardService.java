package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SystemDashboardService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final int LOG_TAIL_BYTES = 64 * 1024;

    private final DataSource dataSource;
    private final HttpRequestMetricsRegistry metricsRegistry;
    private final ObjectMapper objectMapper;

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
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }

    public Map<String, Object> getDbHealth() {
        Map<String, Object> db = new LinkedHashMap<>();
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            db.put("connected", true);
            db.put("pingMs", (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (Exception ex) {
            db.put("connected", false);
            db.put("lastError", ex.getMessage());
            log.warn("Database health check failed", ex);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("db", db);
        return payload;
    }

    public Map<String, Object> getHttpMetrics() {
        return metricsRegistry.snapshot();
    }

    public Map<String, Object> getLogs(String level, int limit) {
        List<Map<String, Object>> logs = resolveLogFile()
            .map(path -> readLogs(path, level, limit))
            .orElse(List.of());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logs", logs);
        return payload;
    }

    private Optional<Path> resolveLogFile() {
        List<Path> candidates = List.of(
            Path.of("data", "logs", "app.log"),
            Path.of("logs", "app.log"),
            Path.of("backend", "data", "logs", "app.log")
        );
        return candidates.stream().filter(Files::exists).findFirst();
    }

    private List<Map<String, Object>> readLogs(Path logFile, String level, int limit) {
        try {
            String raw = readTail(logFile);
            String[] lines = raw.split("\\R");
            List<Map<String, Object>> entries = new ArrayList<>();
            for (int i = lines.length - 1; i >= 0 && entries.size() < limit; i--) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                Map<String, Object> parsed = parseLogLine(line);
                String parsedLevel = String.valueOf(parsed.getOrDefault("level", "info"));
                if (!"all".equalsIgnoreCase(level) && !parsedLevel.equalsIgnoreCase(level)) {
                    continue;
                }
                entries.add(parsed);
            }
            return entries;
        } catch (IOException ex) {
            log.warn("Failed to read application log file {}", logFile, ex);
            return List.of();
        }
    }

    private String readTail(Path logFile) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            long start = Math.max(0, length - LOG_TAIL_BYTES);
            file.seek(start);
            byte[] buffer = new byte[(int) (length - start)];
            file.readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> parseLogLine(String line) {
        try {
            Map<String, Object> json = objectMapper.readValue(line, MAP_TYPE);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ts", json.getOrDefault("ts", json.get("timestamp")));
            payload.put("level", String.valueOf(json.getOrDefault("level", "info")).toLowerCase());
            payload.put("msg", sanitize(String.valueOf(json.getOrDefault("msg", json.getOrDefault("message", line)))));
            payload.put("module", json.getOrDefault("module", json.getOrDefault("logger", "app")));
            payload.put("reqId", json.getOrDefault("reqId", json.getOrDefault("requestId", "-")));
            return payload;
        } catch (Exception ignored) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ts", Instant.now().toString());
            payload.put("level", inferLevel(line));
            payload.put("msg", sanitize(line));
            payload.put("module", "app");
            payload.put("reqId", "-");
            return payload;
        }
    }

    private String inferLevel(String line) {
        String lower = line.toLowerCase();
        if (lower.contains(" error ") || lower.startsWith("error")) {
            return "error";
        }
        if (lower.contains(" warn ") || lower.startsWith("warn")) {
            return "warn";
        }
        if (lower.contains(" debug ") || lower.startsWith("debug")) {
            return "debug";
        }
        return "info";
    }

    private String sanitize(String message) {
        return message
            .replaceAll("(?i)(password|token|authorization)=\\S+", "$1=***")
            .replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer ***");
    }
}