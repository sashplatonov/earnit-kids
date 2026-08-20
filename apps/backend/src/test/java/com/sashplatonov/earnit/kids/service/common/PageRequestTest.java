package com.sashplatonov.earnit.kids.service.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestTest {

    @Test
    void of_clampsPageToAtLeastOne() {
        assertThat(PageRequest.of(0, 50, 100).page()).isEqualTo(1);
    }

    @Test
    void of_clampsLimitToAtLeastOne() {
        assertThat(PageRequest.of(2, 0, 100).limit()).isEqualTo(1);
    }

    @Test
    void of_clampsLimitToMaxLimit() {
        assertThat(PageRequest.of(1, 999, 100).limit()).isEqualTo(100);
    }

    @Test
    void offset_returnsPageMinusOneTimesLimit() {
        assertThat(PageRequest.of(2, 50, 100).offset()).isEqualTo(50);
    }

    @Test
    void offset_firstPageIsZero() {
        assertThat(PageRequest.of(1, 20, 100).offset()).isEqualTo(0);
    }
}
