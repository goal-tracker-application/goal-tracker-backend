package com.goal_tracker.exceptions;

import com.goal_tracker.errors.ErrorCode;

/**
 * Abstract base class for all business logic exceptions in the system.
 * <p>
 * Allows centralizing typed error handling in the {@code GlobalExceptionHandler},
 * ensuring that every domain failure explicitly exposes its {@link ErrorCode}.
 * </p>
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Constructs a new business exception.
     *
     * @param detailedMessage Explanation of the failure for debugging or logs.
     * @param errorCode       Enum constant typifying the error for the frontend.
     */
    protected BusinessException(String detailedMessage, ErrorCode errorCode) {
        super(detailedMessage);
        this.errorCode = errorCode;
    }

    /**
     * Retrieves the unified error code associated with the business failure.
     *
     * @return The assigned {@link ErrorCode}.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}