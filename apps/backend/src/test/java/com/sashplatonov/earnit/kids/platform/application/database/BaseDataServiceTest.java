package com.sashplatonov.earnit.kids.platform.application.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
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

        assertThat(data).containsKeys("tasks");
        assertThat((List<?>) data.get("tasks")).isNotEmpty();
    }

    @Test
    void saveBaseData_persistsDataToFileAndMemory() throws Exception {
        Path baseDataFile = tempDir.resolve("baseData.json");
        BaseDataService service = new BaseDataService(new ObjectMapper(), baseDataFile);

        Map<String, Object> payload = Map.of(
            "tasks", List.of(Map.of("id", "1", "name", "Read"))
        );

        assertThat(service.saveBaseData(payload)).isTrue();
        assertThat(service.getBaseData()).isEqualTo(payload);
        assertThat(Files.exists(baseDataFile)).isTrue();
        assertThat(Files.readString(baseDataFile)).contains("Read");
    }

    @Test
    void getBaseData_usesShortLivedCacheAndRefreshesAfterTtl() throws Exception {
        Path baseDataFile = tempDir.resolve("baseData.json");
        Files.writeString(baseDataFile, """
            {"tasks":[{"id":"1","name":"Read"}]}
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
            {"tasks":[{"id":"3","name":"Draw"}]}
            """);

        Map<String, Object> cached = service.getBaseData();
        assertThat(cached).isEqualTo(first);

        now.set(now.get().plus(Duration.ofMinutes(11)));
        Map<String, Object> refreshed = service.getBaseData();

        assertThat(refreshed).isNotEqualTo(first);
        assertThat(refreshed.toString()).contains("Draw");
    }

    @Test
    void getBaseData_projectsCatalogByLocaleWithoutChangingMetadata() {
        BaseDataService service = new BaseDataService(new ObjectMapper(), tempDir.resolve("baseData.json"));
        Map<String, Object> source = Map.of(
            "tasks", List.of(),
            "catalog", Map.of(
                "tasks", List.of(Map.of(
                    "id", "task-1", "title", Map.of("en", "🌅 Morning task", "ru", "🌅 Утренняя задача"),
                    "comment", Map.of("en", "Do it", "ru", "Сделай это"),
                    "groupName", Map.of("en", "Morning", "ru", "Утро"),
                    "groupKey", "morning", "coins", 3, "minAge", 6)),
                "rewards", List.of(Map.of(
                    "id", "reward-1", "title", Map.of("en", "🎲 Board game", "ru", "🎲 Настольная игра"),
                    "comment", Map.of("en", "Choose it", "ru", "Выбери её"),
                    "groupName", Map.of("en", "Family", "ru", "Семья"),
                    "groupKey", "family", "price", 8, "maxAge", 14))));
        service.saveBaseData(source);

        Map<String, Object> english = service.getBaseData(FamilyLocale.en);
        Map<String, Object> russian = service.getBaseData(FamilyLocale.ru);
        List<?> enTasks = (List<?>) ((Map<?, ?>) english.get("catalog")).get("tasks");
        List<?> ruTasks = (List<?>) ((Map<?, ?>) russian.get("catalog")).get("tasks");
        Map<?, ?> enTask = (Map<?, ?>) enTasks.get(0);
        Map<?, ?> ruTask = (Map<?, ?>) ruTasks.get(0);

        assertThat(enTask.get("title")).isEqualTo("🌅 Morning task");
        assertThat(ruTask.get("title")).isEqualTo("🌅 Утренняя задача");
        assertThat(enTask.get("id")).isEqualTo(ruTask.get("id"));
        assertThat(enTask.get("groupKey")).isEqualTo(ruTask.get("groupKey"));
        assertThat(enTask.get("coins")).isEqualTo(ruTask.get("coins"));
        assertThat(((List<?>) ((Map<?, ?>) english.get("catalog")).get("rewards")).get(0)).isInstanceOf(Map.class);
    }

    @Test
    void getBaseData_legacyFlatCatalogFieldsFallbackForEveryLocale() throws Exception {
        Path baseDataFile = tempDir.resolve("baseData.json");
        Files.writeString(baseDataFile, """
            {"tasks":[],"catalog":{"tasks":[{"id":"legacy-task","title":"🌅 Старая задача","comment":"","groupName":"Утро","groupKey":"morning","coins":1}],"rewards":[]}}
            """);
        BaseDataService service = new BaseDataService(new ObjectMapper(), baseDataFile);

        Map<?, ?> task = (Map<?, ?>) ((List<?>) ((Map<?, ?>) service.getBaseData(FamilyLocale.en).get("catalog")).get("tasks")).get(0);
        assertThat(task.get("title")).isEqualTo("🌅 Старая задача");
        List<?> russianTasks = (List<?>) ((Map<?, ?>) service.getBaseData(FamilyLocale.ru).get("catalog")).get("tasks");
        assertThat(russianTasks).isNotEmpty();
    }
}
