package com.goal_tracker.goals.entity;

/**
 * Represents the lifecycle statuses of a goal within the system.
 * <p>
 * Each enum constant is associated with a unique numerical identifier that maps
 * directly to the master lookup table {@code goal_statuses} in the database.
 * </p>
 * @see GoalStatus
 */
public enum GoalStatusEnum {

    /**
     * The goal is active and under development.
     */
    IN_PROGRESS(1),

    /**
     * The goal was fulfilled and successfully completed.
     */
    COMPLETED(2),

    /**
     * The goal was archived to preserve history without affecting the active dashboard.
     */
    ARCHIVED(3);

    /**
     * The internal numerical identifier stored in the database column.
     */
    private final int id;

    /**
     * Internal enum constructor to associate the numerical identifier.
     *
     * @param id Numerical identifier mapping to the database domain.
     */
    GoalStatusEnum(int id) {
        this.id = id;
    }

    /**
     * Gets the numerical identifier assigned to the goal status.
     * <p>
     * This value is utilized by the {@code AttributeConverter} to persist
     * the enum state into the corresponding column.
     * </p>
     *
     * @return The integer identifier of the goal status.
     */
    public int getId() {
        return this.id;
    }

    /**
     * Constructs the Enum constant from its database numerical identifier.
     *
     * @param id The numerical identifier to evaluate.
     * @return The corresponding {@link GoalStatusEnum}, or {@code null} if the ID is invalid.
     */
    public static GoalStatusEnum fromId(int id) {
        for (GoalStatusEnum status : GoalStatusEnum.values()) {
            if (status.getId() == id) {
                return status;
            }
        }
        return null;
    }
}