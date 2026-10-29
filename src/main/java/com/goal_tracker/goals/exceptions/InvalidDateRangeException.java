package com.goal_tracker.goals.exceptions;

import java.time.LocalDate;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;

/**
 * Exception thrown when a goal's date configuration violates temporal constraints or business rules.
 * <p>
 * Handles various validation failures including null start dates when end dates are present,
 * start dates scheduled in the past, or end dates preceding start dates.
 * </p>
 *
 * @see BadRequestException
 * @see ErrorCode
 */
public class InvalidDateRangeException extends BadRequestException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code InvalidDateRangeException} for missing start dates when an end date is defined.
     * Maps to {@link ErrorCode#ERR_GOAL_START_DATE_REQUIRED_WHEN_END_DATE_PRESENT}.
     */
    public InvalidDateRangeException() {
        super(
            "Goal start date cannot be null while the end date is not null.",
            ErrorCode.ERR_GOAL_START_DATE_REQUIRED_WHEN_END_DATE_PRESENT
        );
    }

    /**
     * Constructs a new {@code InvalidDateRangeException} when a goal's start date is scheduled in the past.
     * Maps to {@link ErrorCode#ERR_GOAL_START_DATE_IN_PAST}.
     *
     * @param startDate the invalid start date that occurred in the past
     */
    public InvalidDateRangeException(LocalDate startDate) {
        super(
            String.format("Goal start date %s cannot be in the past.", startDate),
            ErrorCode.ERR_GOAL_START_DATE_IN_PAST
        );
    }

    /**
     * Constructs a new {@code InvalidDateRangeException} when a goal's end date precedes its start date.
     * Maps to {@link ErrorCode#ERR_GOAL_END_DATE_BEFORE_START}.
     *
     * @param startDate the start date of the goal
     * @param endDate   the invalid end date that occurs before the start date
     */
    public InvalidDateRangeException(LocalDate startDate, LocalDate endDate) {
        super(
            String.format("Goal end date %s cannot be before start date %s.", endDate, startDate),
            ErrorCode.ERR_GOAL_END_DATE_BEFORE_START
        );
    }
}