package com.goal_tracker.goals.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Entity representing an audit log entry for every status transition of a goal,
 * storing the target status via {@link GoalStatusEnum}, optional user notes, and the timestamp of the event.
 */
@Getter
@Setter
@Entity
@Table(name = "goal_status_histories")
public class GoalStatusHistory {

    /**
     * Unique numerical identifier for the status history entry.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The goal to which this history record belongs.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    /**
     * Business enum status of the goal transition.
     */
    @Column(name = "status_id", nullable = false, columnDefinition = "goal_status_type")
    private GoalStatusEnum status;

    /**
     * Read-only master entity relationship for the goal status.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", insertable = false, updatable = false)
    private GoalStatus statusRegister;

    /**
     * Optional text notes or reasons provided by the user for the status change.
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Timestamp indicating exactly when the status transition occurred.
     */
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    /**
     * Default no-arg constructor required by JPA.
     */
    public GoalStatusHistory() {
        super();
    }

    /**
     * Standard constructor for application usage.
     *
     * @param goal      The goal being modified.
     * @param status    The target status enum.
     * @param notes     Optional text notes or reasons.
     * @param changedAt The timestamp of the transition.
     */
    public GoalStatusHistory(Goal goal, GoalStatusEnum status, String notes, OffsetDateTime changedAt) {
        super();
        this.goal = goal;
        this.status = status;
        this.notes = notes;
        this.changedAt = changedAt != null ? changedAt : OffsetDateTime.now();
    }

    /**
     * Test constructor with ID for simulation purposes.
     *
     * @param id        The unique identifier.
     * @param goal      The goal being modified.
     * @param status    The target status enum.
     * @param notes     Optional text notes or reasons.
     * @param changedAt The timestamp of the transition.
     */
    public GoalStatusHistory(Long id, Goal goal, GoalStatusEnum status, String notes, OffsetDateTime changedAt) {
        this.id = id;
        this.goal = goal;
        this.status = status;
        this.notes = notes;
        this.changedAt = changedAt != null ? changedAt : OffsetDateTime.now();
    }
}