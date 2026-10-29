package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.ForbiddenException;

/**
 * Exception thrown when a user attempts to access or modify a goal
 * that does not belong to them.
 */
public class GoalOwnershipException extends ForbiddenException {

    /**
     * Constructs a new goal ownership exception with a detailed message.
     *
     * @param goalId The identifier of the goal.
     * @param userId The identifier of the user.
     */
    public GoalOwnershipException(Long goalId, Long userId) {
        super(String.format("Goal with ID %d does not belong to user with ID %d.", goalId, userId));
    }
}