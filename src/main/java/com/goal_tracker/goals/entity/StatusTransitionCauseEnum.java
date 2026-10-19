package com.goal_tracker.goals.entity;

/**
 * Represents the origin or cause of a goal status transition within the system.
 * <p>
 * Each enum constant is associated with a unique numerical identifier that maps
 * directly to the master lookup table {@code status_transition_causes} in the database.
 * </p>
 * @see StatusTransitionCause
 */
public enum StatusTransitionCauseEnum {

    /**
     * Direct transition triggered explicitly by the user.
     */
    USER(1),

    /**
     * Automatic transition triggered upwards by an event on a child goal.
     */
    UPWARD_CASCADE(2),

    /**
     * Automatic transition triggered downwards by an event on a parent goal.
     */
    DOWNWARD_CASCADE(3);

    /**
     * The internal numerical identifier stored in the database column.
     */
    private final int id;

    /**
     * Internal enum constructor to associate the numerical identifier.
     *
     * @param id Numerical identifier mapping to the database domain.
     */
    StatusTransitionCauseEnum(int id) {
        this.id = id;
    }

    /**
     * Gets the numerical identifier assigned to the transition cause.
     * <p>
     * This value is utilized by the {@code AttributeConverter} to persist
     * the enum state into the corresponding column.
     * </p>
     *
     * @return The integer identifier of the transition cause.
     */
    public int getId() {
        return this.id;
    }

    /**
     * Constructs the Enum constant from its database numerical identifier.
     *
     * @param id The numerical identifier to evaluate.
     * @return The corresponding {@link StatusTransitionCauseEnum}, or {@code null} if the ID is invalid.
     */
    public static StatusTransitionCauseEnum fromId(int id) {
        for (StatusTransitionCauseEnum cause : StatusTransitionCauseEnum.values()) {
            if (cause.getId() == id) {
                return cause;
            }
        }
        return null;
    }
}