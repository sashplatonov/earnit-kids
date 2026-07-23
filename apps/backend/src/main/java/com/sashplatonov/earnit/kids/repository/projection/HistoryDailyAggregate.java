package com.sashplatonov.earnit.kids.repository.projection;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;

import java.time.LocalDate;

public record HistoryDailyAggregate(LocalDate date, HistoryEntryType type, long amount) {
}
