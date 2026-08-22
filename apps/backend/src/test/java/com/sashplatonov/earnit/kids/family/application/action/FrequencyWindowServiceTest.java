package com.sashplatonov.earnit.kids.family.application.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FrequencyWindowServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final FrequencyWindowService service = new FrequencyWindowService();

    @Test
    void resolveCurrentWindow_dstForward_usesLocalMidnightAndTwentyThreeHourDay() {
        FrequencyWindow window = service.resolveCurrentWindow(
            frequency("day", 2),
            Instant.parse("2026-03-29T12:00:00Z"),
            ZoneId.of("Europe/Belgrade")
        ).orElseThrow();

        assertThat(window.start()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
        assertThat(Duration.between(window.start(), window.end())).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void resolveCurrentWindow_dstBackward_usesTwentyFiveHourDay() {
        FrequencyWindow window = service.resolveCurrentWindow(
            frequency("day", 1),
            Instant.parse("2026-10-25T12:00:00Z"),
            ZoneId.of("Europe/Belgrade")
        ).orElseThrow();

        assertThat(Duration.between(window.start(), window.end())).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void resolveCurrentWindow_sameInstant_respectsEachFamilyTimezone() {
        Instant now = Instant.parse("2026-07-18T00:30:00Z");

        FrequencyWindow utc = service.resolveCurrentWindow(frequency("day", 1), now, ZoneId.of("UTC"))
            .orElseThrow();
        FrequencyWindow losAngeles = service.resolveCurrentWindow(
            frequency("day", 1), now, ZoneId.of("America/Los_Angeles")
        ).orElseThrow();

        assertThat(utc.start()).isEqualTo(Instant.parse("2026-07-18T00:00:00Z"));
        assertThat(losAngeles.start()).isEqualTo(Instant.parse("2026-07-17T07:00:00Z"));
    }

    private static com.fasterxml.jackson.databind.JsonNode frequency(String period, int limit) {
        return OBJECT_MAPPER.createObjectNode()
            .put("period", period)
            .put("limit", limit);
    }
}
