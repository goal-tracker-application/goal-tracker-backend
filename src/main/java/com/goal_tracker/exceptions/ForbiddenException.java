package com.goal_tracker.exceptions;

import com.goal_tracker.errors.ErrorCode;

/**
 * Exception thrown when an authenticated user attempts to access a resource 
 * or perform an action for which they lack sufficient permissions (HTTP 403 Forbidden).
 */
public class ForbiddenException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Constructs a new forbidden exception with a custom detail message.
     *
     * @param detailedMessage Explanation of the authorization failure.
     */
    public ForbiddenException(String detailedMessage) {
        super(detailedMessage);
        this.errorCode = ErrorCode.ERR_FORBIDDEN;
    }

    /**
     * Constructs a new forbidden exception using the default error code message.
     */
    public ForbiddenException() {
        super(ErrorCode.ERR_FORBIDDEN.getDefaultMessage());
        this.errorCode = ErrorCode.ERR_FORBIDDEN;
    }

    /**
     * Retrieves the unified error code associated with the access denial.
     *
     * @return The assigned {@link ErrorCode}.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}