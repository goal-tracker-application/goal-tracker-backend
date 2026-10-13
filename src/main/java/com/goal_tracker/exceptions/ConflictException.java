package com.goal_tracker.exceptions;

import com.goal_tracker.errors.ErrorCode;

/**
 * Abstract base class for system exceptions representing state conflicts (HTTP 409 Conflict).
 * <p>
 * Primarily used in response to unique key collisions or structural graph cycles
 * to prevent uncontrolled database-level failures and provide a structured response to the client.
 * </p>
 */
public abstract class ConflictException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Constructs a new conflict exception.
     *
     * @param detailedMessage Detailed explanation of the collision for logs or debugging.
     * @param errorCode       Enum constant typifying the conflict error.
     */
    protected ConflictException(String detailedMessage, ErrorCode errorCode) {
        super(detailedMessage);
        this.errorCode = errorCode;
    }

    /**
     * Retrieves the typed error code associated with the conflict.
     *
     * @return The {@link ErrorCode} assigned by the subclass.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}