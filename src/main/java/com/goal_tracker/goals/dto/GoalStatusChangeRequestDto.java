package com.goal_tracker.goals.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Immutable record DTO for requesting a status transition on a goal using its database status ID,
 * accompanied by optional audit notes matching the migration schema.
 */
public record GoalStatusChangeRequestDto(

    /**
     * The database identifier of the target status. Must not be null and must fall within valid range.
     */
    @NotNull(message = "Status ID is required.")
    @Min(value = 1, message = "Invalid status ID.")
    @Max(value = 6, message = "Invalid status ID.")
    Integer statusId,

    /**
     * Optional audit notes explaining the status change, matching the database schema column.
     * Capped at 500 characters.
     */
    @Size(max = 500, message = "Status notes cannot exceed 500 characters.")
    String notes

) {}