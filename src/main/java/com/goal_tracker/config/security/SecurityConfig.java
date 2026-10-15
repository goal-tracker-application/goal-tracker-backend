package com.goal_tracker.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.goal_tracker.config.security.filters.JwtAuthenticationFilter;
import com.goal_tracker.config.security.filters.RateLimitingFilter;
import com.goal_tracker.config.security.handler.JwtAuthenticationEntryPoint;

/**
 * HTTP security configuration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * Dependency injection for the 401 exception entry point, JWT filter, Rate Limiting filter, and CORS source.
     */
    public SecurityConfig(JwtAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Configures the HTTP security filter chain, CORS integration, stateless session policy,
     * and public/protected access rules for endpoints.
     *
     * @param http Instance of {@link HttpSecurity}.
     * @return Configured {@link SecurityFilterChain}.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        .anyRequest().authenticated()
                )

                // Enforce IP-based rate limiting before the JWT and standard filters
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * Bean for password encoding and verification using the BCrypt algorithm.
     *
     * @return Instance of {@link PasswordEncoder} based on {@link BCryptPasswordEncoder}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}