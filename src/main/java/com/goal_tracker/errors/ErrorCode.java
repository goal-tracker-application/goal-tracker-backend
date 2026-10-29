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
    ERR_PASSWORD_MISMATCH("The provided password do not match its confirmation."),
    
    /**
     * HTTP 400 - Bad Request.
     * Occurs when the provided current password for password update is incorrect.
     */
    INVALID_CURRENT_PASSWORD("The provided current password is incorrect."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when a subgoal's start date is earlier than its ancestor's start date.
     */
    ERR_GOAL_START_DATE_VIOLATION("The subgoal's start date cannot be earlier than its ancestor's start date."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when a subgoal's end date is later than its ancestor's end date.
     */
    ERR_GOAL_END_DATE_VIOLATION("The subgoal's end date cannot be later than its ancestor's end date."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to transition a goal to its current status.
     */
    ERR_GOAL_STATUS_UNCHANGED("The goal is already in the requested status."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to modify a goal that has already reached a terminal state.
     */
    ERR_GOAL_TERMINAL_STATE("Cannot modify a goal that has already reached a terminal state."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting an invalid goal status transition.
     */
    ERR_INVALID_GOAL_TRANSITION("The requested goal status transition is not allowed."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when the provided goal status identifier is invalid or not found.
     */
    ERR_GOAL_STATUS_NOT_FOUND("The provided goal status identifier is invalid."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to modify the start date of a goal that is already in progress.
     */
    ERR_GOAL_START_DATE_IMMUTABLE("Cannot modify the start date of a goal that is already in progress."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to modify the end date of a goal that is currently paused.
     */
    ERR_GOAL_END_DATE_IMMUTABLE("Cannot modify the end date of a goal that is currently paused."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to set or leave the start date as null for a goal that requires it.
     */
    ERR_GOAL_START_DATE_REQUIRED("The start date cannot be null for a goal that is in progress or paused."),


   /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to set or leave the start date as null while the end date is not null.
     */
    ERR_GOAL_START_DATE_REQUIRED_WHEN_END_DATE_PRESENT("The start date cannot be null while the end date is not null."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to set the start date in the past.
     */
    ERR_GOAL_START_DATE_IN_PAST("The start date cannot be in the past."),

    /**
     * HTTP 400 - Bad Request.
     * Occurs when attempting to set the end date before the start date.
     */
    ERR_GOAL_END_DATE_BEFORE_START("The end date cannot be before the start date."),

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
     * HTTP 405 - Method Not Allowed.
     * Occurs when the HTTP method
     * is not supported by the endpoint.
     */
    ERR_METHOD_NOT_SUPPORTED("The requested HTTP method is not supported by the endpoint."),

    /**
     * HTTP 409 - Conflict.
     * Occurs when attempting to register a user with an email address 
     * that is already occupied by another account.
     */
    ERR_EMAIL_ALREADY_EXISTS("The entered email address is already registered."),

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