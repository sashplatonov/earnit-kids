package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemOverviewResponse(
    ProcessStats process,
    OperatingSystemStats os,
    String timestamp
) {
    public record ProcessStats(
        long rssBytes,
        long heapUsedBytes,
        long uptimeSec
    ) { }

    public record OperatingSystemStats(
        Double loadAvg1,
        Double loadAvg5,
        Double loadAvg15,
        int availableProcessors
    ) { }
}
