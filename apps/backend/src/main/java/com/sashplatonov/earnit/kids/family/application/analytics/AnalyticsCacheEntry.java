package com.sashplatonov.earnit.kids.family.application.analytics;

import com.sashplatonov.earnit.kids.family.api.response.AnalyticsResponse;

import java.time.Instant;

record AnalyticsCacheEntry(Instant cachedAt, AnalyticsResponse payload) {
}
