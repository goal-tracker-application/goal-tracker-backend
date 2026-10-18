package com.goal_tracker.goals.exceptions;

import java.time.LocalDate;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;

/**
 * Exception thrown when a subgoal's end date is later than its ancestor's end date.
 */
public class GoalEndDateViolationException extends BadRequestException {

    /**
     * Constructs a new end date violation exception with the conflicting dates.
     *
     * @param subgoalEndDate  The end date of the subgoal.
     * @param ancestorEndDate The end date of the ancestor.
     */
    public GoalEndDateViolationException(LocalDate subgoalEndDate, LocalDate ancestorEndDate) {
        super(
            String.format("Subgoal end date %s cannot be later than its ancestor end date %s.", 
                subgoalEndDate, ancestorEndDate),
            ErrorCode.ERR_GOAL_END_DATE_VIOLATION
        );
    }
}