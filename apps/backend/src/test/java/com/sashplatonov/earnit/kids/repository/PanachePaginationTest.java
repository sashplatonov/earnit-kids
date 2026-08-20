package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.service.common.PageRequest;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PanachePaginationTest {

    @Test
    void page_callsRangeWithDerivedOffsetAndEnd() {
        PanacheQuery<String> query = mock(PanacheQuery.class);
        when(query.range(50, 99)).thenReturn(query);
        when(query.list()).thenReturn(List.of("a", "b"));

        List<String> result = PanachePagination.page(query, PageRequest.of(2, 50, 100));

        assertThat(result).containsExactly("a", "b");
        verify(query).range(50, 99);
    }

    @Test
    void page_withLimitAndOffset_callsRange() {
        PanacheQuery<String> query = mock(PanacheQuery.class);
        when(query.range(10, 19)).thenReturn(query);
        when(query.list()).thenReturn(List.of("x"));

        List<String> result = PanachePagination.page(query, 10, 10);

        assertThat(result).containsExactly("x");
        verify(query).range(10, 19);
    }
}
