package com.sashplatonov.earnit.kids.family.infrastructure.persistence.history;

import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;

import java.time.LocalDate;

public record HistoryDailyAggregate(LocalDate date, HistoryEntryType type, long amount) {
}
