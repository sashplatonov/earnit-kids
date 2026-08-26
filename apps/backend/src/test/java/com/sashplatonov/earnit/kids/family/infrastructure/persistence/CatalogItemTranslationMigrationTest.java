package com.sashplatonov.earnit.kids.family.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogItemTranslationMigrationTest {

    @Test
    void migrationNormalizesV53CatalogWithoutChangingLegacyColumns() throws Exception {
        String url = "jdbc:h2:mem:catalog-translations-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            executeMigration(connection, "V53__seed_localized_catalog.sql");
            String legacyTitle = read(connection, "SELECT title_en FROM catalog_items WHERE external_id = 'ct-6-8-1'");
            String legacyGroup = read(connection, "SELECT group_name_ru FROM catalog_items WHERE external_id = 'ct-6-8-1'");

            executeMigration(connection, "V54__normalize_catalog_item_translations.sql");

            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_item_translations")).isEqualTo(276);
            assertThat(count(connection, "SELECT COUNT(*) FROM (SELECT catalog_item_id FROM catalog_item_translations GROUP BY catalog_item_id HAVING COUNT(*) <> 2) violations")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_item_translations WHERE locale_code IN ('en', 'ru')")).isEqualTo(276);
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_item_translations WHERE TRIM(title) = '' OR TRIM(group_name) = ''")).isZero();
            assertThat(read(connection, "SELECT title FROM catalog_item_translations t JOIN catalog_items i ON i.id = t.catalog_item_id WHERE i.external_id = 'ct-6-8-1' AND locale_code = 'en'"))
                .containsIgnoringCase("wash").doesNotContain("Complete a morning & evening goal");
            assertThat(read(connection, "SELECT title FROM catalog_item_translations t JOIN catalog_items i ON i.id = t.catalog_item_id WHERE i.external_id = 'ct-6-8-1' AND locale_code = 'ru'"))
                .contains("Умыться");

            execute(connection, "INSERT INTO catalog_item_translations (catalog_item_id, locale_code, title, group_name) "
                + "SELECT id, 'sr', 'Јутарња рутина', 'Јутро и вече' FROM catalog_items WHERE external_id = 'ct-6-8-1'");
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_item_translations WHERE locale_code = 'sr'")).isEqualTo(1);

            assertThatThrownBy(() -> execute(connection, "INSERT INTO catalog_item_translations (catalog_item_id, locale_code, title, group_name) "
                + "SELECT id, 'sr', 'Duplicate', 'Group' FROM catalog_items WHERE external_id = 'ct-6-8-1'"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "INSERT INTO catalog_item_translations (catalog_item_id, locale_code, title, group_name) "
                + "VALUES (999999, 'de', 'Orphan', 'Group')"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "INSERT INTO catalog_item_translations (catalog_item_id, locale_code, title, group_name) "
                + "SELECT id, 'de', '', 'Group' FROM catalog_items WHERE external_id = 'ct-6-8-1'"))
                .isInstanceOf(SQLException.class);

            assertThat(read(connection, "SELECT title_en FROM catalog_items WHERE external_id = 'ct-6-8-1'"))
                .isEqualTo(legacyTitle);
            assertThat(read(connection, "SELECT group_name_ru FROM catalog_items WHERE external_id = 'ct-6-8-1'"))
                .isEqualTo(legacyGroup);
            assertThat(columnExists(connection, "title_en")).isTrue();
            assertThat(columnExists(connection, "title_ru")).isTrue();
            assertThat(columnExists(connection, "comment_en")).isTrue();
            assertThat(columnExists(connection, "comment_ru")).isTrue();
            assertThat(columnExists(connection, "group_name_en")).isTrue();
            assertThat(columnExists(connection, "group_name_ru")).isTrue();
        }
    }

    private void executeMigration(Connection connection, String fileName) throws Exception {
        try (InputStream stream = Objects.requireNonNull(getClass().getClassLoader()
            .getResourceAsStream("db/migration/" + fileName))) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    execute(connection, statement);
                }
            }
        }
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private String read(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private boolean columnExists(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CATALOG_ITEMS' AND COLUMN_NAME = ?")) {
            statement.setString(1, column.toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1) == 1;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
