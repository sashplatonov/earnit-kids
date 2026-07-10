package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.response.ApplicationLogsResponse;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApplicationLogService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final int LOG_TAIL_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;

    public ApplicationLogsResponse getLogs(String level, int limit) {
        List<ApplicationLogsResponse.ApplicationLogEntry> logs = resolveLogFile()
            .map(path -> readLogs(path, level, limit))
            .orElse(List.of());
        return new ApplicationLogsResponse(logs);
    }

    List<ApplicationLogsResponse.ApplicationLogEntry> readLogs(Path logFile, String level, int limit) {
        try {
            String raw = readTail(logFile);
            String[] lines = raw.split("\\R");
            List<ApplicationLogsResponse.ApplicationLogEntry> entries = new ArrayList<>();
            for (int i = lines.length - 1; i >= 0 && entries.size() < limit; i--) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                ApplicationLogsResponse.ApplicationLogEntry parsed = parseLogLine(line);
                String parsedLevel = parsed.level();
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

    private Optional<Path> resolveLogFile() {
        List<Path> candidates = List.of(
            Path.of("data", "logs", "app.log"),
            Path.of("logs", "app.log"),
            Path.of("backend", "data", "logs", "app.log")
        );
        return candidates.stream().filter(Files::exists).findFirst();
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

    private ApplicationLogsResponse.ApplicationLogEntry parseLogLine(String line) {
        try {
            java.util.Map<String, Object> json = objectMapper.readValue(line, MAP_TYPE);
            return new ApplicationLogsResponse.ApplicationLogEntry(
                stringOrNull(json.containsKey("ts") ? json.get("ts") : json.get("timestamp")),
                stringOrDefault(json.get("level"), "info").toLowerCase(),
                sanitize(stringOrDefault(json.containsKey("msg") ? json.get("msg") : json.get("message"), line)),
                stringOrDefault(json.containsKey("module") ? json.get("module") : json.get("logger"), "app"),
                stringOrDefault(json.containsKey("reqId") ? json.get("reqId") : json.get("requestId"), "-")
            );
        } catch (IOException ex) {
            return new ApplicationLogsResponse.ApplicationLogEntry(
                timeProvider.now().toString(),
                inferLevel(line),
                sanitize(line),
                "app",
                "-"
            );
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

    private String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? fallback : stringValue;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }
}
