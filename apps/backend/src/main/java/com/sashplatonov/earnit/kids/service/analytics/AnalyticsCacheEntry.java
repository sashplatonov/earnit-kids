package com.sashplatonov.earnit.kids.service.analytics;

import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;

import java.time.Instant;

record AnalyticsCacheEntry(Instant cachedAt, AnalyticsResponse payload) {
}
