package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.BadRequestException;
import com.goal_tracker.errors.ErrorCode;

/**
 * Exception thrown when an invalid or non-existent goal status identifier is provided.
 */
public class GoalStatusNotFoundException extends BadRequestException {

    /**
     * Constructs a new {@code GoalStatusNotFoundException} for the specified invalid status ID.
     *
     * @param statusId The numeric identifier of the non-existent goal status.
     */
    public GoalStatusNotFoundException(Integer statusId) {
        super(
            String.format("Goal status with ID %d was not found.", statusId),
            ErrorCode.ERR_GOAL_STATUS_NOT_FOUND
        );
    }
}