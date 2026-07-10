package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SystemOverviewServiceTest {

    @Test
    void getOverview_returnsProcessOsAndTimestamp() {
        Instant fixedNow = Instant.parse("2026-04-16T12:00:00Z");
        SystemOverviewService service = new SystemOverviewService(TestConfigFactory.timeProvider(fixedNow));

        var payload = service.getOverview();

        assertThat(payload.timestamp()).isEqualTo(fixedNow.toString());
        assertThat(payload.process().rssBytes()).isGreaterThan(0L);
        assertThat(payload.process().heapUsedBytes()).isGreaterThan(0L);
        assertThat(payload.process().uptimeSec()).isGreaterThanOrEqualTo(0L);
        assertThat(payload.os().availableProcessors()).isGreaterThan(0);
    }
}
