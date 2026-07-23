package com.sashplatonov.earnit.kids.config.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPathNormalizerTest {

    @Test
    void normalize_producesExactlyOneLeadingSlash() {
        assertThat(RequestPathNormalizer.normalize(null)).isEqualTo("/");
        assertThat(RequestPathNormalizer.normalize("")).isEqualTo("/");
        assertThat(RequestPathNormalizer.normalize("///")).isEqualTo("/");
        assertThat(RequestPathNormalizer.normalize("api/data")).isEqualTo("/api/data");
        assertThat(RequestPathNormalizer.normalize("/api/data")).isEqualTo("/api/data");
        assertThat(RequestPathNormalizer.normalize("//api/data")).isEqualTo("/api/data");
    }
}
