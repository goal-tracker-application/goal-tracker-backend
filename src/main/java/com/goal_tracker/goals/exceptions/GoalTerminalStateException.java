package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.BadRequestException;
import com.goal_tracker.errors.ErrorCode;

/**
 * Exception thrown when attempting to modify a goal that has already reached a terminal state.
 */
public class GoalTerminalStateException extends BadRequestException {

    /**
     * Constructs a new {@code GoalTerminalStateException} for the specified goal ID.
     *
     * @param goalId The unique identifier of the terminal goal.
     */
    public GoalTerminalStateException(Long goalId) {
        super(
            String.format("Goal with ID %d has reached a terminal state and cannot be modified.", goalId),
            ErrorCode.ERR_GOAL_TERMINAL_STATE
        );
    }
}