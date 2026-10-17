package com.goal_tracker.goals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable record DTO optimized for hierarchical tree navigation rendering,
 * providing structural hierarchy, status context, and date boundaries without heavy audit metadata.
 */
public record GoalTreeResponseDto(

    /**
     * Unique identifier of the goal.
     */
    @NotNull(message = "Goal ID is required.")
    Long id,

    /**
     * Title of the goal used for labeling timeline bars or tree nodes.
     */
    @NotBlank(message = "Goal title is required.")
    @Size(max = 255, message = "Goal title cannot exceed 255 characters.")
    String title,

    /**
     * Database identifier of the current goal status (used for visual coloring and rendering states).
     */
    @NotNull(message = "Status ID is required.")
    Integer statusId,

    /**
     * Planned start date for timeline range calculation. Optional.
     */
    LocalDate startDate,

    /**
     * Planned end date for timeline range calculation. Optional.
     */
    LocalDate endDate,

    /**
     * Recursive list of child goals nested under this branch.
     */
    List<GoalTreeResponseDto> subGoals

) {}