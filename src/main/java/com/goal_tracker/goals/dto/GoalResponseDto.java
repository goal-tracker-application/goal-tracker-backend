package com.goal_tracker.goals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable record DTO representing a flat, detailed view of a goal for API responses,
 * optimized for UI rendering and basic metadata.
 */
public record GoalResponseDto(

    /**
     * Unique identifier of the goal.
     */
    @NotNull(message = "Goal ID is required.")
    Long id,

    /**
     * Title of the goal.
     */
    @NotBlank(message = "Goal title is required.")
    @Size(max = 255, message = "Goal title cannot exceed 255 characters.")
    String title,

    /**
     * Detailed description of the goal. Optional.
     */
    @Size(max = 1000, message = "Goal description cannot exceed 1000 characters.")
    String description,

    /**
     * Planned start date. Optional.
     */
    LocalDate startDate,

    /**
     * Planned end date. Optional.
     */
    LocalDate endDate,

    /**
     * Database identifier of the current goal status.
     */
    @NotNull(message = "Status ID is required.")
    Integer statusId,

    /**
     * Optional parent goal identifier if the goal is nested within a hierarchy.
     */
    Long parentId,

    /**
     * Timestamp indicating when the goal was created. Used for UI/auditing.
     */
    @NotNull(message = "Creation timestamp is required.")
    LocalDateTime createdAt,

    /**
     * Timestamp indicating when the goal was last updated. Used for UI/auditing.
     */
    LocalDateTime updatedAt

) {}