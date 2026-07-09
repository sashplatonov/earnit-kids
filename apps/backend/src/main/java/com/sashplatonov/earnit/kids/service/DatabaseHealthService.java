package com.sashplatonov.earnit.kids.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DatabaseHealthService {

    private final DataSource dataSource;

    public Map<String, Object> getDbHealth() {
        Map<String, Object> db = new LinkedHashMap<>();
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            db.put("connected", true);
            db.put("pingMs", (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (Exception ex) {
            db.put("connected", false);
            db.put("lastError", ex.getMessage());
            log.warn("Database health check failed", ex);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("db", db);
        return payload;
    }
}
