package com.goal_tracker.exceptions;

import com.goal_tracker.errors.ErrorCode;

/**
 * Abstract base class for all bad request exceptions in the system.
 * <p>
 * Allows centralizing typed error handling in the {@code GlobalExceptionHandler},
 * ensuring that every domain failure explicitly exposes its {@link ErrorCode}.
 * </p>
 */
public abstract class BadRequestException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Constructs a new bad request exception.
     *
     * @param detailedMessage Explanation of the failure for debugging or logs.
     * @param errorCode       Enum constant typifying the error for the frontend.
     */
    protected BadRequestException(String detailedMessage, ErrorCode errorCode) {
        super(detailedMessage);
        this.errorCode = errorCode;
    }

    /**
     * Retrieves the unified error code associated with the bad request failure.
     *
     * @return The assigned {@link ErrorCode}.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}