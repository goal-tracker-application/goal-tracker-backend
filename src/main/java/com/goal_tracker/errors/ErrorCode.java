package com.goal_tracker.errors;

/**
 * Enumeration that centralizes the API error codes.
 */
public enum ErrorCode {

    /**
     * HTTP 400 - Bad Request.
     * Occurs when request validations fail (e.g., constraints 
     * like @NotNull, @NotBlank, or incorrectly structured data in payloads).
     */
    ERR_VALIDATION_FAILED("The submitted data does not meet the required validations."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when there is a type mismatch in request parameters 
     * (e.g., sending text in a URL parameter that expects a numeric ID).
     */
    ERR_TYPE_MISMATCH("The data type of one of the request parameters is incorrect."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when the provided password does not match its confirmation.
     */
    ERR_PASSWORD_MISMATCH("The provided password do not match it's confirmation."),
    
    /**
     * HTTP 400 - Bad Request.
     * Occurs when the provided current password for password update is incorrect.
     */
    INVALID_CURRENT_PASSWORD("The provided current password is incorrect."),

    /**
     * HTTP 401 - Unauthorized.
     * Occurs when the request lacks valid authentication credentials,
     * the token has expired, or the provided access data is incorrect.
     */
    ERR_UNAUTHORIZED("Not authenticated or invalid credentials."),

    /**
     * HTTP 403 - Forbidden.
     * Occurs when the authenticated user attempts to access a resource 
     * or perform an action for which they lack sufficient permissions.
     */
    ERR_FORBIDDEN("Access denied: insufficient permissions."),

    /**
     * HTTP 404 - Not Found.
     * Occurs when the requested resource 
     * is not available.
     */
    ERR_NOT_FOUND("The requested resource does not exist."),

    /**
     * HTTP 409 - Conflict.
     * Occurs when attempting to register a user with an email address 
     * that is already occupied by another account.
     */
    ERR_EMAIL_ALREADY_EXISTS("The entered email address is already registered."),

    /**
     * HTTP 409 - Conflict.
     * Occurs when attempting to create a goal log (check-in) for a goal 
     * on a date that already has an existing entry.
     */
    ERR_DUPLICATE_GOAL_LOG("A progress entry (check-in) for this goal already exists on the specified date."),

    /**
     * HTTP 409 - Conflict.
     * Occurs when the goal hierarchy is invalid 
     * (e.g., a goal cannot be its own parent or cause circular dependencies).
     */
    ERR_INVALID_GOAL_HIERARCHY("The goal hierarchy is invalid (a goal cannot be its own parent or generate circular dependencies)."),

    /**
     * HTTP 422 - Unprocessable Entity.
     * Occurs when the start date is later than the end date for a goal.
     */
    ERR_GOAL_DATE_RANGE_INVALID("The start date cannot be later than the end date of the goal."),

    /**
     * HTTP 429 - Too Many Requests.
     * Occurs when the client has sent too many requests in a given amount of time 
     * (rate-limiting threshold exceeded).
     */
    ERR_TOO_MANY_REQUESTS("Too many requests. Please try again later."),
    
    /**
     * HTTP 500 - Internal Server Error.
     * Final safety net to capture unexpected system exceptions 
     * (connectivity drops, null pointers, or unforeseen infrastructure failures).
     */
    ERR_INTERNAL_SERVER_ERROR("An unexpected error occurred on our servers.");

    private final String defaultMessage;

    /**
     * Internal constructor of the enum to associate descriptions with codes.
     *
     * @param defaultMessage Human-readable descriptive text regarding the nature of the error.
     */
    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /**
     * Retrieves the default descriptive message associated with the error code.
     *
     * @return String with the error description.
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}