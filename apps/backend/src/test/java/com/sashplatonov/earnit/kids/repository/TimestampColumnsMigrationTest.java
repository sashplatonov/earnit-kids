package com.sashplatonov.earnit.kids.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampColumnsMigrationTest {

    @Test
    void migrationAddsUpdatedAtColumnsAndPreservesExistingCreatedAtValues() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:timestamp-columns-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            executeMigration(connection, "db/migration/V1__initial_schema.sql");

            Instant historyCreatedAt = Instant.parse("2024-04-17T09:30:15Z");
            Instant friendCreatedAt = Instant.parse("2024-04-16T07:45:00Z");

            int familyId = insertFamily(connection);
            int childId = insertChild(connection, familyId, "Alice");
            int friendChildId = insertChild(connection, familyId, "Bob");
            long historyId = insertHistory(connection, familyId, childId, historyCreatedAt);
            long friendId = insertFriend(connection, childId, friendChildId, friendCreatedAt);

            executeMigration(connection, "db/migration/V7__add_missing_updated_at_and_protect_created_at.sql");

            assertThat(readTimestamp(connection, "history", "updated_at", historyId)).isEqualTo(historyCreatedAt);
            assertThat(readTimestamp(connection, "friends", "updated_at", friendId)).isEqualTo(friendCreatedAt);

            updateHistoryDescription(connection, historyId, "Updated after migration");

            assertThat(readTimestamp(connection, "history", "created_at", historyId)).isEqualTo(historyCreatedAt);
            assertThat(readTimestamp(connection, "history", "updated_at", historyId)).isAfter(historyCreatedAt);
        }
    }

    private int insertFamily(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO families (family_id, email, admin_password) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            String familyId = "family-" + System.nanoTime();
            statement.setString(1, familyId);
            statement.setString(2, familyId + "@test.com");
            statement.setString(3, "secret123");
            statement.executeUpdate();
            return readGeneratedInt(statement);
        }
    }

    private int insertChild(Connection connection, int familyId, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO children (family_id, name, token) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, familyId);
            statement.setString(2, name);
            statement.setString(3, name.toLowerCase() + "-token-" + System.nanoTime());
            statement.executeUpdate();
            return readGeneratedInt(statement);
        }
    }

    private long insertHistory(Connection connection, int familyId, int childId, Instant createdAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO history (family_id, child_id, external_id, type, amount, description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, familyId);
            statement.setInt(2, childId);
            statement.setLong(3, System.nanoTime());
            statement.setString(4, "earn");
            statement.setInt(5, 5);
            statement.setString(6, "Read");
            statement.setObject(7, createdAt);
            statement.executeUpdate();
            return readGeneratedLong(statement);
        }
    }

    private long insertFriend(Connection connection, int childId, int friendChildId, Instant createdAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO friends (child_id, friend_child_id, created_at) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, childId);
            statement.setInt(2, friendChildId);
            statement.setObject(3, createdAt);
            statement.executeUpdate();
            return readGeneratedLong(statement);
        }
    }

    private void updateHistoryDescription(Connection connection, long historyId, String description) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE history SET description = ? WHERE id = ?")) {
            statement.setString(1, description);
            statement.setLong(2, historyId);
            statement.executeUpdate();
        }
    }

    private Instant readTimestamp(Connection connection, String tableName, String columnName, long rowId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT " + columnName + " FROM " + tableName + " WHERE id = ?")) {
            statement.setLong(1, rowId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getObject(1, OffsetDateTime.class).toInstant();
            }
        }
    }

    private int readGeneratedInt(PreparedStatement statement) throws Exception {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getInt(1);
        }
    }

    private long readGeneratedLong(PreparedStatement statement) throws Exception {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private void executeMigration(Connection connection, String resourceName) throws Exception {
        String sql = readSqlResource(resourceName);
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                executeStatement(connection, trimmed);
            }
        }
    }

    private String readSqlResource(String resourceName) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(inputStream, resourceName);
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(content.split("\\R"))
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        }
    }

    private void executeStatement(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}