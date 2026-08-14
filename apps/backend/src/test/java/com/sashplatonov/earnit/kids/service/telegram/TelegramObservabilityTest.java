package com.sashplatonov.earnit.kids.service.telegram;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramObservabilityTest {
    @Test
    void recordsAggregateOutboxMetricWithoutPayload() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TelegramObservability observability = new TelegramObservability(registry);
        observability.outbox("retried");

        assertThat(observability.correlationId()).isNotBlank();
        assertThat(registry.get("earnit.telegram.events").counter().count()).isEqualTo(1);
    }
}
