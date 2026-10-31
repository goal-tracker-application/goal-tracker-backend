package com.goal_tracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration class to provide HTTP client builders as Spring beans.
 */
@Configuration
public class RestClientConfig {

    /**
     * Provides a singleton RestClient.Builder bean to the application context.
     *
     * @return a new instance of RestClient.Builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}