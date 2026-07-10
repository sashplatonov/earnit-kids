package com.sashplatonov.earnit.kids.service.database;

import com.sashplatonov.earnit.kids.dto.response.DatabaseHealthResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DatabaseHealthService {

    private final DataSource dataSource;

    public DatabaseHealthResponse getDbHealth() {
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return new DatabaseHealthResponse(
                new DatabaseHealthResponse.DbHealth(true, (System.nanoTime() - startedAt) / 1_000_000L, null)
            );
        } catch (Exception ex) {
            log.warn("Database health check failed", ex);
            return new DatabaseHealthResponse(
                new DatabaseHealthResponse.DbHealth(false, null, ex.getMessage())
            );
        }
    }
}
