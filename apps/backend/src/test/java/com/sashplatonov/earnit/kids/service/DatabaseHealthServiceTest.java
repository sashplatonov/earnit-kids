package com.sashplatonov.earnit.kids.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseHealthServiceTest {

    @Test
    void getDbHealth_successMarksDatabaseAsConnected() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);

        DatabaseHealthService service = new DatabaseHealthService(dataSource);

        Map<String, Object> payload = service.getDbHealth();

        Map<?, ?> db = (Map<?, ?>) payload.get("db");
        assertThat(db.get("connected")).isEqualTo(true);
        assertThat((Long) db.get("pingMs")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void getDbHealth_failureReportsErrorMessage() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

        DatabaseHealthService service = new DatabaseHealthService(dataSource);

        Map<String, Object> payload = service.getDbHealth();

        Map<?, ?> db = (Map<?, ?>) payload.get("db");
        assertThat(db.get("connected")).isEqualTo(false);
        assertThat(db.get("lastError")).isEqualTo("db down");
    }
}
