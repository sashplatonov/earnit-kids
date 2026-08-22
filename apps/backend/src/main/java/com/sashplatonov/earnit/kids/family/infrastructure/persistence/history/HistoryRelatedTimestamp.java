package com.sashplatonov.earnit.kids.family.infrastructure.persistence.history;

import java.time.Instant;

public record HistoryRelatedTimestamp(long relatedId, Instant timestamp) {
}
