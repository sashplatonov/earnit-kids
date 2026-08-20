package com.sashplatonov.earnit.kids.service.common;

// EXPLAIN: Static pagination math helpers backing PageRequest, kept separate so repository and resource layers can reuse the clamping/offset logic without a record.
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
