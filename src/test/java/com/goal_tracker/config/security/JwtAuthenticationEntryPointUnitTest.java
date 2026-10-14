package com.goal_tracker.config.security;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.errors.ErrorResponse;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

class JwtAuthenticationEntryPointUnitTest {

    private JwtAuthenticationEntryPoint authenticationEntryPoint;
    private ObjectMapper objectMapper;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
            .withCoercionConfig(LogicalType.Integer, config -> 
                config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            )
            .build();
        
        authenticationEntryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    /**
     * Happy path: Attempted access without authentication.
     * <p>
     * <b>Test Design (AAA):</b>
     * <ul>
     * <li><b>Arrange:</b> Instantiates an {@link AuthenticationException} with a descriptive message and prepares request/response mocks.</li>
     * <li><b>Act:</b> Invokes the {@code commence} method.</li>
     * <li><b>Assert:</b> Verifies that the HTTP status code is 401 (SC_UNAUTHORIZED), the content type is JSON,
     * and the response body contains the application's standard error structure fields.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("commence: Should respond with HTTP 401 and structured JSON error body")
    void commenceShouldReturnStatus401AndStructuredJsonErrorMessage() throws IOException {
        String exceptionMessage = "No valid access token was provided";
        AuthenticationException authException = new InsufficientAuthenticationException(exceptionMessage);

        authenticationEntryPoint.commence(request, response, authException);

        assertThat(response.getStatus(), is(MockHttpServletResponse.SC_UNAUTHORIZED));
        assertThat(response.getContentType(), is(MediaType.APPLICATION_JSON_VALUE));

        String jsonResponse = response.getContentAsString();
        assertThat(jsonResponse, is(notNullValue()));
        assertThat(jsonResponse, is(not(emptyString())));

        ErrorResponse errorObtenido = objectMapper.readValue(jsonResponse, ErrorResponse.class);
        
        assertThat(errorObtenido, is(notNullValue()));
        assertThat(errorObtenido.getCode(), is(ErrorCode.ERR_UNAUTHORIZED.name()));
        assertThat(errorObtenido.getMessage(), is(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()));
        assertThat(errorObtenido.getDetails(), contains(exceptionMessage));
        assertThat(errorObtenido.getTrackingId(), is(notNullValue()));
        assertThat(errorObtenido.getTrackingId(), is(not(emptyString())));
    }

    /**
     * Resilience: Handling null or generic exception messages.
     * <p>
     * Verifies that if the exception thrown by Spring Security lacks a detailed message,
     * the class still responds with status 401 and serializes the structure without breaking flow.
     * </p>
     */
    @Test
    @DisplayName("commence: Should correctly process exceptions with null message")
    void commenceShouldProcessExceptionWithNullMessage() throws IOException {
        AuthenticationException authException = new InsufficientAuthenticationException(null);

        authenticationEntryPoint.commence(request, response, authException);

        assertThat(response.getStatus(), is(MockHttpServletResponse.SC_UNAUTHORIZED));

        String jsonResponse = response.getContentAsString();
        ErrorResponse errorObtenido = objectMapper.readValue(jsonResponse, ErrorResponse.class);

        assertThat(errorObtenido, is(notNullValue()));
        assertThat(errorObtenido.getCode(), is(ErrorCode.ERR_UNAUTHORIZED.name()));
        assertThat(errorObtenido.getDetails(), is(empty()));
        assertThat(errorObtenido.getTrackingId(), is(notNullValue()));
    }
}