package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.BadRequestException;
import com.goal_tracker.errors.ErrorCode;

/**
 * Exception thrown when attempting to modify the start date of a goal that is already in progress.
 */
public class GoalStartDateImmutableException extends BadRequestException {

    /**
     * Constructs a new {@code GoalStartDateImmutableException} for the specified goal ID.
     *
     * @param goalId The unique identifier of the goal whose start date is immutable.
     */
    public GoalStartDateImmutableException(Long goalId) {
        super(
            String.format("Goal with ID %d is already in progress and its start date cannot be modified.", goalId),
            ErrorCode.ERR_GOAL_START_DATE_IMMUTABLE
        );
    }
}