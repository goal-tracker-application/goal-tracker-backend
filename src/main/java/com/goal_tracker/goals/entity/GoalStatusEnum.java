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
     * The goal is created or scheduled and awaiting activation.
     */
    WAITING(1),

    /**
     * The goal is actively being pursued within its execution timeframe.
     */
    IN_PROGRESS(2),

    /**
     * The goal execution is temporarily frozen; time elapsed is deferred upon unpausing.
     */
    PAUSED(3),

    /**
     * The goal objective was successfully achieved.
     */
    COMPLETED(4),

    /**
     * The goal was missed or expired past its end date without completion.
     */
    FAILED(5),

    /**
     * The goal was dropped or cancelled without being marked as a failure.
     */
    DISCARDED(6);

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