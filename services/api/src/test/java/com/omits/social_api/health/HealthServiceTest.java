package com.omits.social_api.health;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @Test
    void reportsUpWhenDatabaseConnectionIsValid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        HealthResponse response = new HealthService(dataSource).check();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.checks()).containsEntry("api", "UP");
        assertThat(response.checks()).containsEntry("database", "UP");
    }

    @Test
    void reportsDownWhenDatabaseConnectionIsInvalid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        HealthResponse response = new HealthService(dataSource).check();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.checks()).containsEntry("database", "DOWN");
    }

    @Test
    void reportsDownWhenDatabaseConnectionThrows() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        HealthResponse response = new HealthService(dataSource).check();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.checks()).containsEntry("api", "UP");
        assertThat(response.checks()).containsEntry("database", "DOWN");
    }
}
