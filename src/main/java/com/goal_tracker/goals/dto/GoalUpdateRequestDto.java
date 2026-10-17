package com.goal_tracker.goals.dto;

import com.goal_tracker.goals.validation.DateRangeAware;
import com.goal_tracker.goals.validation.ValidDateRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Immutable record DTO for updating an existing goal's basic properties,
 * featuring field-level validations and cross-field date range integrity checks.
 */
@ValidDateRange
public record GoalUpdateRequestDto(
    /**
     * The updated title of the goal. Must not be blank and cannot exceed 255 characters.
     */
    @NotBlank(message = "Goal title is required.")
    @Size(max = 255, message = "Goal title cannot exceed 255 characters.")
    String title,

    /**
     * The updated detailed description of the goal. Optional, capped at 1000 characters.
     */
    @Size(max = 1000, message = "Goal description cannot exceed 1000 characters.")
    String description,

    /**
     * The updated planned start date. Optional, but if provided, cannot be set in the past.
     */
    LocalDate startDate,

    /**
     * The updated planned end date. Optional, but restricted by cross-field validation rules (requires startDate).
     */
    LocalDate endDate
) implements DateRangeAware {

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalDate getStartDate() {
        return this.startDate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalDate getEndDate() {
        return this.endDate;
    }
}