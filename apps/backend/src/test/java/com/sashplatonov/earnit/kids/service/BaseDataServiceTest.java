package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
}
