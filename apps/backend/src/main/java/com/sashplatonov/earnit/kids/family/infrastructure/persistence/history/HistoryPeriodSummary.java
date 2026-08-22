package com.sashplatonov.earnit.kids.family.infrastructure.persistence.history;

public record HistoryPeriodSummary(int earned, int spent) {
    public static final HistoryPeriodSummary EMPTY = new HistoryPeriodSummary(0, 0);

    public int net() {
        return earned - spent;
    }
}
