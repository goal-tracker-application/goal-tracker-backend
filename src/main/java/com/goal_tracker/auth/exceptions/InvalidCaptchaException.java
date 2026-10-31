package com.goal_tracker.auth.exceptions;

import com.goal_tracker.auth.dto.CaptchaResponseDto;
import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;
import org.springframework.http.HttpStatusCode;

/**
 * Exception thrown when CAPTCHA validation fails.
 * <p>
 * Results in an HTTP 400 Bad Request response with the {@link ErrorCode#ERR_INVALID_CAPTCHA} code.
 * </p>
 */
public class InvalidCaptchaException extends BadRequestException {

    private static final String DEFAULT_PROVIDER_REJECTED_MESSAGE = "External CAPTCHA provider rejected the token.";
    private static final String DEFAULT_NULL_RESPONSE_MESSAGE = "CAPTCHA provider returned a null response body.";


    /**
     * Constructs an exception when the provider explicitly rejects the token or has a null response
     *
     * @param response the {@link CaptchaResponseDto} returned from the online validator
     */
    public InvalidCaptchaException(CaptchaResponseDto response) {
        super(
            response == null  ? DEFAULT_NULL_RESPONSE_MESSAGE: DEFAULT_PROVIDER_REJECTED_MESSAGE, 
            ErrorCode.ERR_INVALID_CAPTCHA
        );
    }

    /**
     * Constructs an exception based on an HTTP status code from the verification service (e.g., 500, 503).
     *
     * @param status the HTTP status received from the provider
     */
    public InvalidCaptchaException(HttpStatusCode status) {
        super(
            String.format("Failed communication with CAPTCHA provider, status: %s", status), 
            ErrorCode.ERR_INVALID_CAPTCHA
        );
    }

    /**
     * Constructs an exception wrapping a root cause or communication error.
     *
     * @param cause the underlying exception that caused the failure
     */
    public InvalidCaptchaException(Throwable cause) {
        super(
            String.format("Error communicating with CAPTCHA service: %s", cause.getMessage()), 
            ErrorCode.ERR_INVALID_CAPTCHA
        );
    }
}