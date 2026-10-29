package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.BadRequestException;
import com.goal_tracker.errors.ErrorCode;

/**
 * Exception thrown when attempting to clear the start date of a goal that requires it to be present.
 */
public class GoalStartDateRequiredException extends BadRequestException {

    /**
     * Constructs a new {@code GoalStartDateRequiredException} for the specified goal ID.
     *
     * @param goalId The unique identifier of the goal.
     */
    public GoalStartDateRequiredException(Long goalId) {
        super(
            String.format("Goal with ID %d requires a valid start date in its current status.", goalId),
            ErrorCode.ERR_GOAL_START_DATE_REQUIRED
        );
    }
}