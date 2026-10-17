package com.goal_tracker.goals.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a goal within the system, supporting recursive parent-child hierarchies (sub-goals)
 * and status tracking via {@link GoalStatusEnum}.
 */
@Getter
@Setter
@Entity
@Table(name = "goals")
@EntityListeners(AuditingEntityListener.class)
public class Goal {

    /**
     * Unique numerical identifier for the goal.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identifier of the user who owns this goal.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Parent goal in case this is a sub-goal.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Goal parent;

    /**
     * List of child goals (sub-goals) associated with this goal.
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Goal> subGoals = new ArrayList<>();

    /**
     * Title or short summary of the goal.
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Detailed description of the goal.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Planned start date for the goal.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * Planned end (due) date for the goal.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Business enum status of the goal. Defaults to WAITING (1).
     */
    @Column(name = "status_id", nullable = false)
    private GoalStatusEnum status = GoalStatusEnum.WAITING;

    /**
     * Read-only master entity relationship for the goal status.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", insertable = false, updatable = false)
    private GoalStatus statusRegister;

    /**
     * Timestamp when the goal was created.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Timestamp when the goal was last updated.
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Default no-arg constructor required by JPA.
     */
    public Goal() {
        super();
    }

    /**
     * Standard constructor for application usage.
     */
    public Goal(Long userId, Goal parent, String title, String description, LocalDate startDate, LocalDate endDate, GoalStatusEnum status) {
        super();
        this.userId = userId;
        this.parent = parent;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * Test constructor with ID and createdAt (unmodified goal simulation).
     */
    public Goal(Long id, Long userId, Goal parent, String title, String description, LocalDate startDate, LocalDate endDate, GoalStatusEnum status, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.parent = parent;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        if (status != null) {
            this.status = status;
        }
        this.createdAt = createdAt;
    }

    /**
     * Full test constructor with ID, createdAt and updatedAt (modified goal simulation).
     */
    public Goal(Long id, Long userId, Goal parent, String title, String description, LocalDate startDate, LocalDate endDate, GoalStatusEnum status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.parent = parent;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        if (status != null) {
            this.status = status;
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}