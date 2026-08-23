package com.sashplatonov.earnit.kids.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ParentInvitationDigestKeyMigrationTest {

    @Test
    void h2MigrationBackfillsLegacyPendingInvitationKeyIdentifier() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:parent-invitation-key-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            executeMigration(connection, "db/migration/V1__initial_schema.sql");
            insertFamily(connection);
            createPreMigrationInvitationTable(connection);
            insertPendingInvitation(connection);

            executeMigration(connection, "db/migration/V46__add_parent_invitation_digest_key_id.sql");

            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery(
                     "SELECT token_digest_key_id FROM parent_email_invitations")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("legacy");
            }
        }
    }

    private void insertFamily(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO families (family_id, email, admin_password) VALUES (?, ?, ?)")) {
            statement.setString(1, "family-key-test");
            statement.setString(2, "owner@example.com");
            statement.setString(3, "hash");
            statement.executeUpdate();
        }
    }

    private void insertPendingInvitation(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO parent_email_invitations "
                + "(family_id, normalized_email, permission, token_digest, status, expires_at, invited_by_email) "
                + "VALUES (1, 'parent@example.com', 'viewer', 'legacy-digest', 'pending', CURRENT_TIMESTAMP, "
                + "'owner@example.com')")) {
            statement.executeUpdate();
        }
    }

    private void createPreMigrationInvitationTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE parent_email_invitations ("
                + "id SERIAL PRIMARY KEY, family_id INTEGER NOT NULL, normalized_email VARCHAR(320) NOT NULL, "
                + "permission VARCHAR(32) NOT NULL, token_digest VARCHAR(128) NOT NULL UNIQUE, "
                + "status VARCHAR(16) NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "invited_by_email VARCHAR(320) NOT NULL)");
        }
    }

    private void executeMigration(Connection connection, String resourceName) throws Exception {
        for (String sql : readSqlResource(resourceName).split(";")) {
            String statement = sql.trim();
            if (!statement.isEmpty()) {
                try (Statement sqlStatement = connection.createStatement()) {
                    sqlStatement.execute(statement);
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
