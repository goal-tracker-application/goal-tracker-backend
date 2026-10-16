package com.goal_tracker.goals.entity;

import jakarta.persistence.*;
import lombok.Getter;

import org.hibernate.annotations.Immutable;

/**
 * entity representing the states for goals.
 * <p>
 * This class is marked as {@link Immutable}, indicating that it is strictly for 
 * READ-ONLY operations at the ORM level. Hibernate will not attempt to insert, 
 * update, or delete records in this table from the application.
 * </p>
 */
@Entity
@Table(name = "goal_statuses")
@Getter 
@Immutable
public class GoalStatus {

    /**
     * Unique numerical identifier for the goal state (1: IN_PROGRESS, 2: COMPLETED, 3: ARCHIVED).
     */
    @Id
    private Integer id;

    /**
     * Unique name of the goal state (e.g., 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED').
     */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Detailed description of the purpose of the state within the goal's lifecycle.
     */
    @Column(length = 255)
    private String description;

    /**
     * Default constructor required by JPA.
     */
    public GoalStatus() {
    }
}