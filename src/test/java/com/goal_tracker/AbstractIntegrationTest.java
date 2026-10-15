package com.goal_tracker;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;


/**
 * Abstract base class for integration tests using Testcontainers via JDBC.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
    @SuppressWarnings("resource")
    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.9-bookworm")
            .withDatabaseName("goal_tracker_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @SuppressWarnings("resource")
    @Container
    protected static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpBase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        });
    }
}