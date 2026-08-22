package com.sashplatonov.earnit.kids.family.infrastructure.persistence.history;

import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;

public record HistoryTypeTotal(HistoryEntryType type, long amount) {
}
