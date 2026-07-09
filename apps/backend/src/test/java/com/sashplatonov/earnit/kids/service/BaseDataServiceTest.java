package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDataServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void getBaseData_bundledResourcePresent_returnsCatalogData() {
        BaseDataService service = new BaseDataService(new ObjectMapper(), tempDir.resolve("baseData.json"));

        Map<String, Object> data = service.getBaseData();

        assertThat(data).containsKeys("tasks", "products");
        assertThat((List<?>) data.get("tasks")).isNotEmpty();
        assertThat((List<?>) data.get("products")).isNotEmpty();
    }

    @Test
    void saveBaseData_persistsDataToFileAndMemory() throws Exception {
        Path baseDataFile = tempDir.resolve("baseData.json");
        BaseDataService service = new BaseDataService(new ObjectMapper(), baseDataFile);

        Map<String, Object> payload = Map.of(
            "tasks", List.of(Map.of("id", "1", "name", "Read")),
            "products", List.of(Map.of("id", "2", "name", "Toy"))
        );

        assertThat(service.saveBaseData(payload)).isTrue();
        assertThat(service.getBaseData()).isEqualTo(payload);
        assertThat(Files.exists(baseDataFile)).isTrue();
        assertThat(Files.readString(baseDataFile)).contains("Read", "Toy");
    }

    @Test
    void getBaseData_usesShortLivedCacheAndRefreshesAfterTtl() throws Exception {
        Path baseDataFile = tempDir.resolve("baseData.json");
        Files.writeString(baseDataFile, """
            {"tasks":[{"id":"1","name":"Read"}],"products":[{"id":"2","name":"Toy"}]}
            """);

        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-16T12:00:00Z"));
        BaseDataService service = new BaseDataService(
            new ObjectMapper(),
            baseDataFile,
            now::get,
            Duration.ofMinutes(10)
        );

        Map<String, Object> first = service.getBaseData();

        Files.writeString(baseDataFile, """
            {"tasks":[{"id":"3","name":"Draw"}],"products":[{"id":"4","name":"Ball"}]}
            """);

        Map<String, Object> cached = service.getBaseData();
        assertThat(cached).isEqualTo(first);

        now.set(now.get().plus(Duration.ofMinutes(11)));
        Map<String, Object> refreshed = service.getBaseData();

        assertThat(refreshed).isNotEqualTo(first);
        assertThat(refreshed.toString()).contains("Draw", "Ball");
    }
}
