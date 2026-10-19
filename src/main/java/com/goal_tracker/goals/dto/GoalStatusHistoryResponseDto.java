package com.goal_tracker.goals.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Immutable record DTO representing an individual audit history entry for a goal's status transition,
 * optimized for timeline and change-log rendering in the UI.
 */
public record GoalStatusHistoryResponseDto(

    /**
     * Database identifier of the status to which the goal transitioned.
     */
    @NotNull(message = "Status ID is required.")
    Integer statusId,

    /**
     * Name string of the cause that triggered the transition (e.g., 'USER', 'UPWARD_CASCADE', 'DOWNWARD_CASCADE').
     */
    @NotNull(message = "Transition cause is required.")
    String cause,

    /**
     * Optional audit notes or reasons provided during the status transition.
     * Capped at 500 characters matching the underlying data constraints.
     */
    @Size(max = 500, message = "Status notes cannot exceed 500 characters.")
    String notes,

    /**
     * Exact timestamp indicating when the status transition occurred.
     */
    @NotNull(message = "Changed timestamp is required.")
    OffsetDateTime changedAt

) {}