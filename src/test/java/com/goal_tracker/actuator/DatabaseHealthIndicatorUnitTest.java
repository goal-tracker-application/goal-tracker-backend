package com.goal_tracker.actuator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatabaseHealthIndicator}.
 */
class DatabaseHealthIndicatorTest {

    private DataSource dataSource;
    private Connection connection;
    private DatabaseHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        connection = Mockito.mock(Connection.class);
        healthIndicator = new DatabaseHealthIndicator(dataSource);
    }

    @Test
    @DisplayName("Should return UP status when database connection is valid")
    void shouldReturnUpWhenDatabaseIsValid() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "UP");
        assertThat(health.getDetails()).containsEntry("application", "Goal Tracker API");
    }

    @Test
    @DisplayName("Should return DOWN status when database connection is invalid")
    void shouldReturnDownWhenDatabaseIsInvalid() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "DOWN");
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("Should return DOWN status when an exception occurs during connection")
    void shouldReturnDownWhenExceptionIsThrown() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "DOWN");
        assertThat(health.getDetails()).containsEntry("error", "Database connection is invalid or unreachable.");
    }
}