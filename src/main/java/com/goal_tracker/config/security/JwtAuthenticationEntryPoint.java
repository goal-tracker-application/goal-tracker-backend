package com.goal_tracker.config.security;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.errors.ErrorResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Authentication entry point for Spring Security.
 * Intercepts unauthenticated requests (or requests with invalid/expired tokens) 
 * and returns the standard API JSON error structure with HTTP 401 status.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, 
                         HttpServletResponse response, 
                         AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.ERR_UNAUTHORIZED.name(),
                ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage(),
                authException.getMessage(),
                "ERR-" + UUID.randomUUID().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}