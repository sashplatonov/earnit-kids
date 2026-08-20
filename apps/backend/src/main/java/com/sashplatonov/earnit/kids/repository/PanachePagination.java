package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.service.common.PageRequest;
import io.quarkus.hibernate.orm.panache.PanacheQuery;

import java.util.List;

// EXPLAIN: Wraps the Panache .range(offset, offset + limit - 1).list() pattern so paginated repository queries stay consistent and never repeat the range math.
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
