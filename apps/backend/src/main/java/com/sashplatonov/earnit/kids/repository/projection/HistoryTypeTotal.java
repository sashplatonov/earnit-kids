package com.sashplatonov.earnit.kids.repository.projection;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;

public record HistoryTypeTotal(HistoryEntryType type, long amount) {
}
