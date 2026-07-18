package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskPeriodProgressDto(
    String period,
    int completed,
    int pending,
    int limit,
    int remaining,
    boolean available,
    Instant windowStart,
    Instant resetAt
) { }
