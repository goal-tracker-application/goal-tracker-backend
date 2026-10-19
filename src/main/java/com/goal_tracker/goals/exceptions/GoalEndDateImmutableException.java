package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.BadRequestException;
import com.goal_tracker.errors.ErrorCode;

/**
 * Exception thrown when attempting to modify the end date of a goal that is currently paused.
 */
public class GoalEndDateImmutableException extends BadRequestException {

    /**
     * Constructs a new {@code GoalEndDateImmutableException} for the specified goal ID.
     *
     * @param goalId The unique identifier of the paused goal.
     */
    public GoalEndDateImmutableException(Long goalId) {
        super(
            String.format("Goal with ID %d is currently paused and its end date cannot be modified.", goalId),
            ErrorCode.ERR_GOAL_END_DATE_IMMUTABLE
        );
    }
}