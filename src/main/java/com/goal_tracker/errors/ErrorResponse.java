package com.goal_tracker.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.List;

/**
 * Data Transfer Object (DTO) standardized for representing error responses in the API.
 * <p>
 * Provides a unified and semantic contract to report failures to the client, omitting empty 
 * collections during JSON serialization via {@link JsonInclude.Include#NON_EMPTY}.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

    /**
     * Textual identifier of the error code (associated with the {@link ErrorCode} enumeration).
     */
    private final String code;

    /**
     * Main descriptive message of the error intended for human reading.
     */
    private final String message;

    /**
     * List with complementary information or reasons explaining the origin of the failure.
     */
    private final List<String> details;

    /**
     * Global Unique Identifier (Tracking ID) assigned to the event for traceability and log correlation.
     */
    private final String trackingId;

    /**
     * Default constructor created for deserialization.
     */
    public ErrorResponse() {
        this(null, null, Collections.emptyList(), null);
    }

    /**
     * Constructor for simple errors that do not require additional details.
     *
     * @param code       Functional identifier of the error.
     * @param message    Main description of the error.
     * @param trackingId Unique correlation identifier for auditing.
     */
    public ErrorResponse(String code, String message, String trackingId) {
        this(code, message, Collections.emptyList(), trackingId);
    }

    /**
     * Constructor for errors that feature a single textual specification or detail.
     *
     * @param code       Functional identifier of the error.
     * @param message    Main description of the error.
     * @param detail     Complementary clarifying text about the cause of the failure.
     * @param trackingId Unique correlation identifier for auditing.
     */
    public ErrorResponse(String code, String message, String detail, String trackingId) {
        this(code, message, detail != null ? List.of(detail) : Collections.emptyList(), trackingId);
    }

    /**
     * Complete constructor for multiple details structured as a list of messages.
     *
     * @param code       Functional identifier of the error.
     * @param message    Main description of the error.
     * @param details    Collection of clarifying messages (e.g., field validation errors).
     * @param trackingId Unique correlation identifier for auditing.
     */
    public ErrorResponse(String code, String message, List<String> details, String trackingId) {
        this.code = code;
        this.message = message;
        this.details = details != null ? details : Collections.emptyList();
        this.trackingId = trackingId;
    }

    /**
     * Gets the error code.
     *
     * @return The string identifier of the error code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the main descriptive message of the error.
     *
     * @return Text with the general description of the issue.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the list of specific details associated with the error.
     *
     * @return A {@link List} of strings with the failure specifications.
     */
    public List<String> getDetails() {
        return details;
    }

    /**
     * Gets the unique tracking code assigned to the request.
     *
     * @return The tracking identifier for support or debugging.
     */
    public String getTrackingId() {
        return trackingId;
    }
}