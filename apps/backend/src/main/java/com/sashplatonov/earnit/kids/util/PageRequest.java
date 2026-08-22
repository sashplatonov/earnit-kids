package com.sashplatonov.earnit.kids.util;

public record PageRequest(int page, int limit) {

    public static PageRequest of(int page, int limit, int maxLimit) {
        int clampedPage = Math.max(page, 1);
        int clampedLimit = Math.min(Math.max(limit, 1), maxLimit);
        return new PageRequest(clampedPage, clampedLimit);
    }

    public int offset() {
        return (page - 1) * limit;
    }
}
