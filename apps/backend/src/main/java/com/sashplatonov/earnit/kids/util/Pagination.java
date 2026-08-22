package com.sashplatonov.earnit.kids.util;

public final class Pagination {

    private Pagination() {
    }

    public static int clampLimit(int limit, int maxLimit) {
        return Math.min(Math.max(limit, 1), maxLimit);
    }

    public static int offset(int page, int limit) {
        return (Math.max(page, 1) - 1) * limit;
    }
}
