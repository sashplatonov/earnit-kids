package com.sashplatonov.earnit.kids.repository.projection;

public record HistoryPeriodSummary(int earned, int spent) {
    public static final HistoryPeriodSummary EMPTY = new HistoryPeriodSummary(0, 0);

    public int net() {
        return earned - spent;
    }
}
