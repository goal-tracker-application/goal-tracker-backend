package com.goal_tracker.actuator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

/**
 * Custom health indicator for monitoring database connectivity
 * and application operational status within Spring Boot Actuator.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private final DataSource dataSource;

    /**
     * Constructor for injecting the application DataSource.
     *
     * @param dataSource the data source used to verify database connectivity.
     */
    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Evaluates the health status of the application and its database connection.
     *
     * @return a {@link Health} object representing UP or DOWN status along with details.
     */
    @Override
    public Health health() {
        String dbStatus = "UP";
        boolean isConnected = false;

        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) { // 2-second timeout for validation
                isConnected = true;
            } else {
                dbStatus = "DOWN";
            }
        } catch (Exception e) {
            log.error("Database health check failed: Unable to establish connection.", e);
            dbStatus = "DOWN";
        }

        if (isConnected) {
            return Health.up()
                    .withDetail("application", "Goal Tracker API")
                    .withDetail("database", dbStatus)
                    .withDetail("timestamp", LocalDateTime.now())
                    .build();
        } else {
            return Health.down()
                    .withDetail("application", "Goal Tracker API")
                    .withDetail("database", dbStatus)
                    .withDetail("timestamp", LocalDateTime.now())
                    .withDetail("error", "Database connection is invalid or unreachable.")
                    .build();
        }
    }
}