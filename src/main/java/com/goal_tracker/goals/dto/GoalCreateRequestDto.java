package com.goal_tracker.goals.dto;

import com.goal_tracker.goals.validation.DateRangeAware;
import com.goal_tracker.goals.validation.ValidDateRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Immutable record DTO for creating a new goal with field validations and cross-field date checks.
 */
@ValidDateRange
public record GoalCreateRequestDto(
    /**
     * The title of the goal. Must not be blank and cannot exceed 255 characters.
     */
    @NotBlank(message = "Goal title is required.")
    @Size(max = 255, message = "Goal title cannot exceed 255 characters.")
    String title,

    /**
     * Detailed description of the goal. Optional, capped at a reasonable limit of 1000 characters.
     */
    @Size(max = 1000, message = "Goal description cannot exceed 1000 characters.")
    String description,

    /**
     * Planned start date. Optional, but if provided, cannot be set in the past.
     */
    LocalDate startDate,

    /**
     * Planned end date. Optional, but restricted by cross-field validation rules (requires startDate).
     */
    LocalDate endDate,

    /**
     * Optional parent goal identifier for hierarchical goal mapping.
     */
    Long parentId
) implements DateRangeAware {

    /**
     * {@inheritDoc}
     */
    @Override 
    public LocalDate getStartDate(){
        return this.startDate;
    }

    /**
     * {@inheritDoc}
     */
    @Override 
    public LocalDate getEndDate(){
        return this.endDate;
    }
}