package com.sashplatonov.earnit.kids.service.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readLogs_parsesJsonAndPlainTextAndFiltersByLevel() throws Exception {
        Path logFile = tempDir.resolve("app.log");
        Files.writeString(logFile, String.join("\n",
            "{\"ts\":\"2026-04-16T12:00:00Z\",\"level\":\"info\",\"msg\":\"ok\"}",
            "warn backup password=secret",
            "{\"timestamp\":\"2026-04-16T12:01:00Z\",\"level\":\"error\",\"message\":\"token=abc\"}"
        ));

        ApplicationLogService service = new ApplicationLogService(
            new ObjectMapper(),
            TestConfigFactory.timeProvider(Instant.parse("2026-04-16T12:02:00Z"))
        );

        List<com.sashplatonov.earnit.kids.dto.response.ApplicationLogsResponse.ApplicationLogEntry> logs =
            service.readLogs(logFile, "all", 10);

        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).level()).isEqualTo("error");
        assertThat(logs.get(0).msg()).doesNotContain("token=abc");
        assertThat(logs.get(1).level()).isEqualTo("warn");
        assertThat(logs.get(1).msg()).doesNotContain("password=secret");
        assertThat(logs.get(2).level()).isEqualTo("info");
    }

    @Test
    void getLogs_whenNoCandidateExists_returnsEmptyLogs() {
        ApplicationLogService service = new ApplicationLogService(
            new ObjectMapper(),
            TestConfigFactory.timeProvider(Instant.parse("2026-04-16T12:02:00Z"))
        );

        com.sashplatonov.earnit.kids.dto.response.ApplicationLogsResponse payload = service.getLogs("all", 10);

        assertThat(payload.logs()).isEmpty();
    }
}
