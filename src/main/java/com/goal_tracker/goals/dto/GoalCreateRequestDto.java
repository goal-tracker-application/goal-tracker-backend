package com.goal_tracker.goals.dto;

import com.goal_tracker.goals.validation.ValidDateRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO for creating a new goal, featuring flexible nullable dates
 * and robust cross-field integrity checks via custom validation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
public class GoalCreateRequestDto {

    /**
     * Title of the goal. Must not be blank and cannot exceed 255 characters.
     */
    @NotBlank(message = "Goal title is required.")
    @Size(max = 255, message = "Goal title cannot exceed 255 characters.")
    private String title;

    /**
     * Detailed description of the goal. Optional, capped at a reasonable limit of 1000 characters.
     */
    @Size(max = 1000, message = "Goal description cannot exceed 1000 characters.")
    private String description;

    /**
     * Planned start date. Optional, but if provided, cannot be in the past.
     */
    private LocalDate startDate;

    /**
     * Planned end date. Optional, but restricted by cross-field validation rules (requires startDate).
     */
    private LocalDate endDate;

    /**
     * Optional parent goal identifier for hierarchical goal mapping.
     */
    private Long parentId;
}