package com.goal_tracker.auth.exceptions;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.ConflictException;

/**
 * Exception thrown when attempting to register a user with an email that is already in use.
 */
public class EmailAreadyInUseException  extends ConflictException {

    /**
     * Constructs a new EmailAreadyInUseException with a detailed message.
     *
     * @param email The email address that is already in use.
     */
    public EmailAreadyInUseException(String email) {
        super(String.format("The email: %s is already in use", email), ErrorCode.ERR_EMAIL_ALREADY_EXISTS);
    }   
}
