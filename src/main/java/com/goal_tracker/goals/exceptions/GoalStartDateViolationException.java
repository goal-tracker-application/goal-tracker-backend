package com.goal_tracker.goals.exceptions;

import java.time.LocalDate;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;

/**
 * Exception thrown when a subgoal's start date is earlier than its ancestor's start date.
 */
public class GoalStartDateViolationException extends BadRequestException {

    /**
     * Constructs a new start date violation exception with the conflicting dates.
     *
     * @param subgoalStartDate  The start date of the subgoal.
     * @param ancestorStartDate The start date of the ancestor.
     */
    public GoalStartDateViolationException(LocalDate subgoalStartDate, LocalDate ancestorStartDate) {
        super(
            String.format("Subgoal start date %s cannot be earlier than its ancestor start date %s.", 
                subgoalStartDate, ancestorStartDate),
            ErrorCode.ERR_GOAL_START_DATE_VIOLATION
        );
    }
}