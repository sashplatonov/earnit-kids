package com.sashplatonov.earnit.kids.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyIdRandomizationMigrationTest {

    private static final String ORIGINAL_FAMILY_ID = "family-original@example.com";
    private static final String SECOND_FAMILY_ID = "family-second@example.com";

    @Test
    void h2MigrationUpdatesEachFamilyIdOnceWithoutExposingOriginalValue() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:family-id-randomization-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            executeMigration(connection, "db/migration/V1__initial_schema.sql");
            insertFamily(connection, ORIGINAL_FAMILY_ID, "original@example.com");
            insertFamily(connection, SECOND_FAMILY_ID, "second@example.com");

            String migration = readSqlResource("db/migration/V39__randomize_family_ids.sql");
            assertThat(Pattern.compile("(?i)\\bUPDATE\\s+families\\s+SET\\s+family_id")
                .matcher(migration).results().count()).isEqualTo(1);

            executeMigration(connection, "db/migration/V39__randomize_family_ids.sql");

            String randomizedId = readFamilyId(connection, "original@example.com");
            String secondRandomizedId = readFamilyId(connection, "second@example.com");
            assertThat(randomizedId).startsWith("fam_");
            assertThat(secondRandomizedId).startsWith("fam_");
            assertThat(randomizedId).isNotEqualTo(ORIGINAL_FAMILY_ID);
            assertThat(secondRandomizedId).isNotEqualTo(SECOND_FAMILY_ID);
            assertThat(randomizedId).isNotEqualTo(secondRandomizedId);
            assertThat(randomizedId).doesNotContain("original@example.com");
            assertThat(secondRandomizedId).doesNotContain("second@example.com");

            executeMigration(connection, "db/migration/V39__randomize_family_ids.sql");

            assertThat(readFamilyId(connection, "original@example.com")).isEqualTo(randomizedId);
            assertThat(readFamilyId(connection, "second@example.com")).isEqualTo(secondRandomizedId);
        }
    }

    private void insertFamily(Connection connection, String familyId, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO families (family_id, email, admin_password) VALUES (?, ?, ?)")) {
            statement.setString(1, familyId);
            statement.setString(2, email);
            statement.setString(3, "secret123");
            statement.executeUpdate();
        }
    }

    private String readFamilyId(Connection connection, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT family_id FROM families WHERE email = ?")) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private void executeMigration(Connection connection, String resourceName) throws Exception {
        for (String statement : readSqlResource(resourceName).split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                try (Statement sqlStatement = connection.createStatement()) {
                    sqlStatement.execute(trimmed);
                }
            }
        }
    }

    private String readSqlResource(String resourceName) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(inputStream, resourceName);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
