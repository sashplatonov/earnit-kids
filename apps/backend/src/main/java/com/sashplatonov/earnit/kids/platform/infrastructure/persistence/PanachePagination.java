package com.sashplatonov.earnit.kids.platform.infrastructure.persistence;

import com.sashplatonov.earnit.kids.util.PageRequest;
import io.quarkus.hibernate.orm.panache.PanacheQuery;

import java.util.List;

public final class PanachePagination {

    private PanachePagination() {
    }

    public static <T> List<T> page(PanacheQuery<T> query, PageRequest pageRequest) {
        return query.range(pageRequest.offset(), pageRequest.offset() + pageRequest.limit() - 1).list();
    }

    public static <T> List<T> page(PanacheQuery<T> query, int limit, int offset) {
        return query.range(offset, offset + limit - 1).list();
    }
}
