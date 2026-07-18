package com.sashplatonov.earnit.kids.service.family.action;

import java.time.Instant;
import java.time.ZoneId;

public record FrequencyWindow(
    String period,
    int limit,
    Instant start,
    Instant end,
    ZoneId zoneId
) { }
