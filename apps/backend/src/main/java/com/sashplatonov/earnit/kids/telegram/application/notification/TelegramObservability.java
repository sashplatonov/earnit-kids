package com.sashplatonov.earnit.kids.telegram.application.notification;

import com.sashplatonov.earnit.kids.platform.application.observability.BackendKpiMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class TelegramObservability {
    private static final String METRIC = "earnit.telegram.events";
    private final BackendKpiMetrics metrics;

    @Inject
    public TelegramObservability(MeterRegistry meterRegistry) {
        this.metrics = new BackendKpiMetrics(meterRegistry);
    }

    public String correlationId() {
        return UUID.randomUUID().toString();
    }

    public void record(String event, String outcome) {
        metrics.increment(METRIC, "telegram", event, outcome);
    }

    public void webhookAccepted() {
        record("webhook", "accepted");
    }

    public void webhookRejected() {
        record("webhook", "rejected");
    }

    public void webhookDeduplicated() {
        record("webhook", "deduplicated");
    }

    public void callbackOutcome(String outcome) {
        record("callback", outcome);
    }

    public void outbox(String outcome) {
        record("outbox", outcome);
    }

    public void gate(String capability, String outcome) {
        record("gate_" + capability, outcome);
    }
}
