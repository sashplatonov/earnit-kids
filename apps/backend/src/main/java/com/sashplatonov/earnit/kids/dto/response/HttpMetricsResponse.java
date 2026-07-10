package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HttpMetricsResponse(
    HttpMetricsSummary summary,
    List<HttpEndpointMetrics> topEndpoints
) {
    public HttpMetricsResponse {
        topEndpoints = topEndpoints == null ? List.of() : List.copyOf(topEndpoints);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HttpMetricsSummary(
        long totalRequests,
        long errorsTotal,
        double errorRatePct,
        long avgDurationMs
    ) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HttpEndpointMetrics(
        String method,
        String path,
        long count,
        long errors,
        long avgDurationMs,
        long maxDurationMs,
        long avgPayloadBytes,
        long maxPayloadBytes,
        double totalPayloadMb
    ) { }
}
