package com.goal_tracker.config.security.filters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;

import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.auth.dto.RegisterRequest;
import com.goal_tracker.errors.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * Integration test suite for verifying the IP-based rate limiting behavior
 * enforced by the {@link RateLimitingFilter} using MockMvc and Testcontainers.
 */
class RateLimitingIntegrationTest extends AbstractIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Resets the Redis state before each test by flushing all keys, ensuring
     * complete isolation and a fresh token bucket state for every test case.
     */
    @BeforeEach
    void setUp() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        }
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Verifies that requests under the threshold are allowed through the filter,
     * whereas any request exceeding the configured capacity is blocked with an HTTP 429 status
     * and the standard error payload structure.
     *
     * @throws Exception if any MockMvc or request execution error occurs
     */
    @Test
    @DisplayName("Should allow requests up to capacity limit and block with 429 afterwards")
    void testRateLimitingEnforcement() throws Exception {
        String testEndpoint = "/auth/register";

       String clientIp = "192.168.1.100";

        for (int i = 0; i < 5; i++) { 
            RegisterRequest requestBody = new RegisterRequest("test"+i+"@examplo.com", "StrongPassword123!", "StrongPassword123!");
        
            mockMvc.perform(post(testEndpoint)
                            .header("X-Forwarded-For", clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isCreated()); 
        }
        RegisterRequest requestBody = new RegisterRequest("test@examplo.com", "StrongPassword123!", "StrongPassword123!");
        
        mockMvc.perform(post(testEndpoint)
                            .header("X-Forwarded-For", clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TOO_MANY_REQUESTS.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_TOO_MANY_REQUESTS.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId").isNotEmpty());
    }

    /**
     * Verifies that requests targeting endpoints outside the "/auth/" prefix
     * completely bypass the rate-limiting filter, regardless of frequency.
     *
     * @throws Exception if any MockMvc or request execution error occurs
     */
    @Test
    @DisplayName("Should not apply rate limiting to endpoints outside /auth/")
    void testNonAuthEndpointsAreBypassed() throws Exception {
        String publicEndpoint = "/actuator/health";
        String clientIp = "192.168.1.200";

        for (int i = 1; i <= 7; i++) {
            mockMvc.perform(get(publicEndpoint)
                            .header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }
    }
}