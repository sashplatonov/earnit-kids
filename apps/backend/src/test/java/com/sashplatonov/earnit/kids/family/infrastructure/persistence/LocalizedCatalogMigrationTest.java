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

class LocalizedCatalogMigrationTest {

    @Test
    void migrationSeedsCompleteLocalizedCatalogAndEnforcesInvariants() throws Exception {
        String url = "jdbc:h2:mem:localized-catalog-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            executeMigration(connection);

            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items")).isEqualTo(138);
            assertThat(count(connection, "SELECT COUNT(DISTINCT external_id) FROM catalog_items")).isEqualTo(138);
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE title_en IS NULL OR TRIM(title_en) = ''")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE title_ru IS NULL OR TRIM(title_ru) = ''")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE group_name_en IS NULL OR TRIM(group_name_en) = ''")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE group_name_ru IS NULL OR TRIM(group_name_ru) = ''")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE item_type = 'task' AND coins > 0 AND price IS NULL")).isEqualTo(72);
            assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE item_type = 'reward' AND price > 0 AND coins IS NULL")).isEqualTo(66);

            for (int[] ages : new int[][]{{6, 8}, {9, 11}, {12, 14}}) {
                assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE item_type = 'task' AND min_age <= ? AND max_age >= ?", ages[0], ages[1])).isGreaterThanOrEqualTo(20);
                assertThat(count(connection, "SELECT COUNT(*) FROM catalog_items WHERE item_type = 'reward' AND min_age <= ? AND max_age >= ?", ages[0], ages[1])).isGreaterThanOrEqualTo(20);
            }

            assertThat(readTitle(connection, "ct-6-8-1", "title_ru")).startsWith("🌅");
            assertThat(readTitle(connection, "ct-6-8-1", "title_en")).containsIgnoringCase("morning");
            assertThat(readTitle(connection, "cr-6-8-1", "title_ru")).contains("настольную игру");
            assertThat(readTitle(connection, "cr-6-8-1", "title_en")).containsIgnoringCase("family");

            assertThatThrownBy(() -> execute(connection,
                "INSERT INTO catalog_items (external_id, item_type, title_en, title_ru, group_key, group_name_en, group_name_ru, semantic_graphic_key, frequency_limit, frequency_period, min_age, max_age, difficulty, is_active, sort_order, coins, price) "
                    + "VALUES ('invalid-task', 'task', 'Task', 'Задание', 'x', 'X', 'X', 'x', 1, 'day', 6, 8, 'simple', TRUE, 1, 1, 1)"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                "INSERT INTO catalog_items (external_id, item_type, title_en, title_ru, group_key, group_name_en, group_name_ru, semantic_graphic_key, frequency_limit, frequency_period, min_age, max_age, difficulty, is_active, sort_order, coins, price) "
                    + "VALUES ('invalid-reward', 'reward', 'Reward', 'Награда', 'x', 'X', 'X', 'x', 1, 'day', 6, 8, 'simple', TRUE, 1, 1, NULL)"))
                .isInstanceOf(SQLException.class);
        }
    }

    private void executeMigration(Connection connection) throws Exception {
        String sql;
        try (InputStream stream = Objects.requireNonNull(getClass().getClassLoader()
            .getResourceAsStream("db/migration/V53__seed_localized_catalog.sql"))) {
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                execute(connection, statement);
            }
        }
    }

    private long count(Connection connection, String sql, int... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setInt(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private String readTitle(Connection connection, String id, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + column + " FROM catalog_items WHERE external_id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
