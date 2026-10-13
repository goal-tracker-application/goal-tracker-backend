package com.goal_tracker.exceptions;

import com.goal_tracker.errors.ErrorCode;

/**
 * Exception used as a root for all scenarios where an explicitly requested resource 
 * does not exist in the system or cannot be found for any reason.
 * <p>
 * Centrally defaults to the {@link ErrorCode#ERR_NOT_FOUND} code 
 * to guide HTTP 404 status code formatting at the API level.
 * </p>
 */
public abstract class NotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Constructs a new not found exception.
     *
     * @param detailedMessage Explanation of the failure for debugging or logs.
     */
    protected NotFoundException(String detailedMessage) {
        super(detailedMessage);
        this.errorCode = ErrorCode.ERR_NOT_FOUND;
    }

    /**
     * Retrieves the unified error code associated with the missing resource failure.
     *
     * @return The assigned {@link ErrorCode}.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}