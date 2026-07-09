package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemOverviewServiceTest {

    @Test
    void getOverview_returnsProcessOsAndTimestamp() {
        Instant fixedNow = Instant.parse("2026-04-16T12:00:00Z");
        SystemOverviewService service = new SystemOverviewService(TestConfigFactory.timeProvider(fixedNow));

        Map<String, Object> payload = service.getOverview();

        assertThat(payload).containsKeys("process", "os", "timestamp");
        assertThat(payload.get("timestamp")).isEqualTo(fixedNow.toString());
        Map process = (Map) payload.get("process");
        Map os = (Map) payload.get("os");
        assertThat(process.containsKey("rssBytes")).isTrue();
        assertThat(process.containsKey("heapUsedBytes")).isTrue();
        assertThat(process.containsKey("uptimeSec")).isTrue();
        assertThat(os.containsKey("loadAvg1")).isTrue();
        assertThat(os.containsKey("loadAvg5")).isTrue();
        assertThat(os.containsKey("loadAvg15")).isTrue();
        assertThat(os.containsKey("availableProcessors")).isTrue();
    }
}
