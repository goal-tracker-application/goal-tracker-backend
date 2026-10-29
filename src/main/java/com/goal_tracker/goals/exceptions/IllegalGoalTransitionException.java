package com.goal_tracker.goals.exceptions;

import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.BadRequestException;
import com.goal_tracker.goals.entity.GoalStatusEnum;

/**
 * Exception thrown when an illegal or unsupported goal status transition is attempted.
 */
public class IllegalGoalTransitionException extends BadRequestException {

        /**
     * Constructs an exception when attempting to transition a goal to its current status.
     *
     * @param goalId  The unique identifier of the goal.
     */
    public IllegalGoalTransitionException(Long goalId) {
        super(
            String.format("Goal #%d is already in the requested status. Status transition is redundant.", goalId),
            ErrorCode.ERR_GOAL_STATUS_UNCHANGED
        );
    }

    /**
     * Constructs an exception when attempting to modify a goal that is already in a terminal state.
     *
     * @param goalId         The unique identifier of the goal.
     * @param currentStatus  The terminal status the goal currently has.
     */
    public IllegalGoalTransitionException(Long goalId, GoalStatusEnum currentStatus) {
        super(
            String.format("Cannot modify goal #%d because it has already reached a terminal state: %s", goalId, currentStatus.name()),
            ErrorCode.ERR_GOAL_TERMINAL_STATE
        );
    }

    /**
     * Constructs an exception when attempting an unsupported or invalid status transition.
     *
     * @param goalId      The unique identifier of the goal.
     * @param fromStatus  The current status of the goal.
     * @param toStatus    The attempted target status.
     */
    public IllegalGoalTransitionException(Long goalId, GoalStatusEnum fromStatus, GoalStatusEnum toStatus) {
        super(
            String.format("Invalid status transition for goal #%d: Cannot transition from %s to %s", goalId, fromStatus.name(), toStatus.name()),
            ErrorCode.ERR_INVALID_GOAL_TRANSITION
        );
    }
}