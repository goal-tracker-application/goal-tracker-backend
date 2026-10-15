package com.goal_tracker.auth.exceptions;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;

/**
 * Exception thrown when the provided password does not match its confirmation.
 */
public class PasswordMismatchException extends BadRequestException {

    /**
     * Constructs a new PasswordMismatchException with a detailed message.
     */
    public PasswordMismatchException() {
        super( ErrorCode.ERR_PASSWORD_MISMATCH.getDefaultMessage(), ErrorCode.ERR_PASSWORD_MISMATCH);
    }
    
}
