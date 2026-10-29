package com.goal_tracker.goals.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Entity representing the transition causes for goal statuses.
 * <p>
 * This class is marked as {@link Immutable}, indicating that it is strictly for 
 * READ-ONLY operations at the ORM level. Hibernate will not attempt to insert, 
 * update, or delete records in this table from the application.
 * </p>
 */
@Entity
@Table(name = "status_transition_causes")
@Getter 
@Immutable
public class StatusTransitionCause {

    /**
     * Unique numerical identifier for the transition cause (1: CREATION, 2: USER, 3: UPWARD_CASCADE, 4: DOWNWARD_CASCADE, 5: AUTO_ACTIVATION, 6: AUTO_FAILURE).
     */
    @Id
    private Integer id;

    /**
     * Unique name of the transition cause (e.g.,'CREATION', 'USER', 'UPWARD_CASCADE', 'DOWNWARD_CASCADE', 'AUTO_ACTIVATION', 'AUTO_FAILURE').
     */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Detailed description of the purpose of the transition cause.
     */
    @Column(length = 255)
    private String description;

    /**
     * Default constructor required by JPA.
     */
    public StatusTransitionCause() {
        super();
    }

    /**
     * Constructs a StatusTransitionCause with its fields.
     *
     * @param id          The unique numerical identifier.
     * @param name        The transition cause name string.
     * @param description The detailed description.
     */
    public StatusTransitionCause(Integer id, String name, String description) {
        super();
        this.id = id;
        this.name = name;
        this.description = description;
    }
}