package com.omits.social_api.health;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthResponse check() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("api", UP);
        checks.put("database", checkDatabase());

        String overallStatus = checks.containsValue(DOWN) ? DOWN : UP;
        return new HealthResponse(overallStatus, checks);
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS) ? UP : DOWN;
        } catch (SQLException e) {
            return DOWN;
        }
    }
}
