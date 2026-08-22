package com.sashplatonov.earnit.kids.family.application.action;

import java.time.Instant;
import java.time.ZoneId;

public record FrequencyWindow(
    String period,
    int limit,
    Instant start,
    Instant end,
    ZoneId zoneId
) { }
