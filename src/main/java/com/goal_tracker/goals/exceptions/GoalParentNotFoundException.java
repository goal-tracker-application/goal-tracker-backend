package com.goal_tracker.goals.exceptions;

import com.goal_tracker.exceptions.NotFoundException;

/**
 * Exception thrown when a specified parent goal intended to contain a new or updated subgoal
 * cannot be found.
 */
public class GoalParentNotFoundException extends NotFoundException {

    /**
     * Constructs a new parent goal not found exception with a descriptive message.
     *
     * @param parentId The identifier of the parent goal that was not found.
     */
    public GoalParentNotFoundException(Long parentId) {
        super(String.format("Parent goal with ID %d was not found or is inaccessible.", parentId));
    }
}