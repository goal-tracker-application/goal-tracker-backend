package com.goal_tracker.goals.validation;

import java.time.LocalDate;

/**
 * Interface representing any object containing start and end dates eligible for range validation.
 * <p>
 * Implemented by request DTOs to provide a unified contract for custom cross-field date validators.
 * </p>
 */
public interface DateRangeAware {

    /**
     * Retrieves the planned start date.
     *
     * @return The start date, or {@code null} if not specified.
     */
    LocalDate getStartDate();

    /**
     * Retrieves the planned end date.
     *
     * @return The end date, or {@code null} if not specified.
     */
    LocalDate getEndDate();
}