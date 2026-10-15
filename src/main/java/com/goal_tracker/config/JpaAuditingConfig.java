package com.goal_tracker.config;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration class for enabling JPA auditing in the application.
 * <p>
 * This class provides a custom {@link DateTimeProvider} to ensure that
 * auditing fields (like created and modified timestamps) are populated
 * with the current date and time in UTC.
 * </p>
 */
@Configuration
@EnableJpaAuditing(
    dateTimeProviderRef = "dateTimeProvider"
)
public class JpaAuditingConfig {
    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}