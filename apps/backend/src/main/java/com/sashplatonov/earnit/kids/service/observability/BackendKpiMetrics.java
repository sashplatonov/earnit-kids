package com.sashplatonov.earnit.kids.service.observability;

import com.sashplatonov.earnit.kids.util.OperationResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BackendKpiMetrics {
    private static final String OPERATION_DURATION_METRIC = "earnit.backend.service.operation.duration";
    private static final String OPERATION_COUNT_METRIC = "earnit.backend.service.operation.count";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_OPERATION = "operation";
    private static final String TAG_OUTCOME = "outcome";

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";

    private final MeterRegistry meterRegistry;

    public <T> OperationResult<T> recordResult(String service,
                                               String operation,
                                               Supplier<OperationResult<T>> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_FAILURE;
        try {
            OperationResult<T> result = action.get();
            outcome = result instanceof OperationResult.Success<?> ? OUTCOME_SUCCESS : OUTCOME_FAILURE;
            return result;
        } catch (RuntimeException ex) {
            outcome = OUTCOME_FAILURE;
            throw ex;
        } finally {
            recordOutcome(service, operation, outcome, sample);
        }
    }

    public void recordVoid(String service, String operation, Runnable action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_SUCCESS;
        try {
            action.run();
        } catch (RuntimeException ex) {
            outcome = OUTCOME_FAILURE;
            throw ex;
        } finally {
            recordOutcome(service, operation, outcome, sample);
        }
    }

    public <T> T recordValue(String service, String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_SUCCESS;
        try {
            return action.get();
        } catch (RuntimeException ex) {
            outcome = OUTCOME_FAILURE;
            throw ex;
        } finally {
            recordOutcome(service, operation, outcome, sample);
        }
    }

    public void increment(String metricName, String service, String operation, String outcome) {
        Counter.builder(metricName)
            .tag(TAG_SERVICE, service)
            .tag(TAG_OPERATION, operation)
            .tag(TAG_OUTCOME, outcome)
            .register(meterRegistry)
            .increment();
    }

    public void registerGauge(String metricName, ConcurrentMap<?, ?> sessions, String service, String description) {
        Gauge.builder(metricName, sessions, map -> map.size())
            .tag(TAG_SERVICE, service)
            .description(description)
            .register(meterRegistry);
    }

    private void recordOutcome(String service, String operation, String outcome, Timer.Sample sample) {
        var timer = Timer.builder(OPERATION_DURATION_METRIC)
            .tag(TAG_SERVICE, service)
            .tag(TAG_OPERATION, operation)
            .tag(TAG_OUTCOME, outcome)
            .register(meterRegistry);
        sample.stop(timer);
        increment(OPERATION_COUNT_METRIC, service, operation, outcome);
    }
}
