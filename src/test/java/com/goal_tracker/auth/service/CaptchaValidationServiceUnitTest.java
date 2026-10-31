package com.goal_tracker.auth.service;

import com.goal_tracker.auth.exceptions.InvalidCaptchaException;
import com.goal_tracker.config.security.CaptchaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit test class for {@link CaptchaValidationService}.
 * <p>
 * Validates the behavior of CAPTCHA token verification against external providers
 * using Spring's {@link MockRestServiceServer} and Mockito.
 * </p>
 */
class CaptchaValidationServiceUnitTest {

    private final String verificationUrl = "https://www.google.com/recaptcha/api/siteverify";
    private CaptchaValidationService captchaValidationService;
    private MockRestServiceServer mockServer;
    private CaptchaProperties captchaProperties;

    /**
     * Sets up the test environment before each test case execution.
     * Mocks configuration properties and initializes the RestClient with a mock server binding.
     */
    @BeforeEach
    void setUp() {
        captchaProperties = Mockito.mock(CaptchaProperties.class);
        when(captchaProperties.getSecretKey()).thenReturn("dummy-secret-key");
        when(captchaProperties.getVerifyUrl()).thenReturn(verificationUrl);

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        captchaValidationService = new CaptchaValidationService(captchaProperties, restClientBuilder);
    }

    /**
     * Tests that validation succeeds when the external provider responds with HTTP 200 OK and success: true.
     */
    @Test
    void validateCaptchaSuccess() {
        mockServer.expect(requestTo(verificationUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("{\"success\": true}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> captchaValidationService.validateCaptcha("valid-token"));
        mockServer.verify();
    }

    /**
     * Tests that an {@link InvalidCaptchaException} is thrown when the external provider returns a non-successful HTTP status (e.g., 500).
     */
    @Test
    void validateCaptchaShouldThrowExceptionWhenHttpStatusIsNotSuccessful() {
        mockServer.expect(requestTo(verificationUrl))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        InvalidCaptchaException exception = assertThrows(
                InvalidCaptchaException.class,
                () -> captchaValidationService.validateCaptcha("token-any")
        );

        assertNotNull(exception);
        mockServer.verify();
    }

    /**
     * Tests that an {@link InvalidCaptchaException} is thrown when the provider responds with HTTP 200 OK but an empty body.
     */
    @Test
    void validateCaptchaShouldThrowExceptionWhenResponseIsNull() {
        mockServer.expect(requestTo(verificationUrl))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThrows(
                InvalidCaptchaException.class,
                () -> captchaValidationService.validateCaptcha("token-any")
        );
        mockServer.verify();
    }

    /**
     * Tests that an {@link InvalidCaptchaException} is thrown when the provider responds with HTTP 200 OK but explicitly rejects the token (success: false).
     */
    @Test
    void validateCaptchaShouldThrowExceptionWhenProviderRejectsToken() {
        mockServer.expect(requestTo(verificationUrl))
                .andRespond(withSuccess("{\"success\": false}", MediaType.APPLICATION_JSON));

        assertThrows(
                InvalidCaptchaException.class,
                () -> captchaValidationService.validateCaptcha("expired-token")
        );
        mockServer.verify();
    }

    /**
     * Tests that a communication or network error during the HTTP request is caught and wrapped into an {@link InvalidCaptchaException}.
     */
    @Test
    void validateCaptchaShouldThrowExceptionWhenNetworkOrCommunicationFails() {
        RestClient restClientMock = Mockito.mock(RestClient.class);
        RestClient.Builder builderMock = Mockito.mock(RestClient.Builder.class);
        
        when(builderMock.build()).thenReturn(restClientMock);
        when(restClientMock.post()).thenThrow(new RuntimeException("Connection timeout"));

        CaptchaValidationService failingService = new CaptchaValidationService(captchaProperties, builderMock);

        assertThrows(
                InvalidCaptchaException.class,
                () -> failingService.validateCaptcha("token-any")
        );
    }
}