package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.NotFoundException;

/**
 * Exception thrown when a requested goal cannot be found in the system.
 */
public class GoalNotFoundException extends NotFoundException {

    /**
     * Constructs a new goal not found exception with a descriptive message.
     *
     * @param goalId The identifier of the goal that was not found.
     */
    public GoalNotFoundException(Long goalId) {
        super(String.format("Goal with ID %d was not found or is inaccessible.", goalId));
    }
}