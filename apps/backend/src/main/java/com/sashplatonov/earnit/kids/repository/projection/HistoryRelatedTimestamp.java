package com.sashplatonov.earnit.kids.repository.projection;

import java.time.Instant;

public record HistoryRelatedTimestamp(long relatedId, Instant timestamp) {
}
