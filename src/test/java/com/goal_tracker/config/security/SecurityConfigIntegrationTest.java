package com.goal_tracker.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.goal_tracker.AbstractIntegrationTest;

import lombok.extern.log4j.Log4j2;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Integration tests for SecurityConfig.
 * 
 * This class tests the security configuration of the application, focusing on public endpoints and CORS behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Log4j2 
class SecurityConfigIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Test to verify that the Actuator Health endpoint is accessible without authentication.
     * This test sends a GET request to the /actuator/health endpoint and expects a 200 OK response.
     */
    @Test
    @DisplayName("Public Endpoint: Should allow access to Actuator Health without authentication")
    void shouldAllowAccessToActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:3000"))
                .andExpect( result ->{
                    int status = result.getResponse().getStatus();
                    log.info("Response status: {}", status);
                    assertEquals(200, status);
                });

    }

    /**
     * Test to verify that requests to the Actuator Health endpoint are blocked by CORS when the origin is not allowed.
     * This test sends a GET request to the /actuator/health endpoint with a disallowed origin and expects a 403 Forbidden response.
     */
    @Test
    @DisplayName("Public Endpoint: Should be blocked by CORS when origin not allowed")
    void shouldCorsShouldBlockRequest() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:9000"))
                .andExpect( result ->{
                    int status = result.getResponse().getStatus();
                    log.info("Response status: {}", status);
                    assertEquals(403, status);
                });

    }

    /**
     * Test to verify that the Actuator Health endpoint returns a 404 Not Found for non-existent sub-endpoints.
     * This test sends a GET request to a non-existent sub-endpoint of /actuator/health and expects a 404 Not Found response.
     */
    @Test
    @DisplayName("Public Endpoint: Should return 404 for non-existent actuatorendpoint")
    void should404WhenAccessingNonExistentActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health/non-existent-endpoint")
                        .header("Origin", "http://localhost:3000"))
                .andExpect( result ->{
                    int status = result.getResponse().getStatus();
                    log.info("Response status: {}", status);
                    assertEquals(404, status);
                });

    }
}