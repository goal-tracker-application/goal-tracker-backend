package com.goal_tracker.goals.repository;

import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link Goal} entities.
 * <p>
 * Provides custom queries for root goal filtering, recursive tree traversal support via EntityGraphs,
 * automated background scheduler maintenance, and secure multi-tenant operations.
 * </p>
 */
@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    /**
     * Finds a goal by its ID and owner, ensuring secure multi-tenant isolation against IDOR vulnerabilities.
     *
     * @param id     The goal identifier.
     * @param userId The owner user identifier.
     * @return An Optional containing the goal if found and owned by the user.
     */
    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    /**
     * Finds a goal by its ID along with its entire recursive sub-goals tree,
     * utilizing an EntityGraph to prevent LazyInitializationException and N+1 query issues.
     *
     * @param id     The goal identifier.
     * @param userId The owner user identifier.
     * @return An Optional containing the goal and its full sub-tree.
     */
    @EntityGraph(attributePaths = {"subGoals", "subGoals.subGoals"}) // Profundidad controlada para árboles anidados
    Optional<Goal> findWithSubGoalsByIdAndUserId(Long id, Long userId);

    /**
     * Retrieves all root goals (top-level goals without a parent) for a specific user.
     *
     * @param userId The owner user identifier.
     * @return List of root goals.
     */
    List<Goal> findByUserIdAndParentIsNull(Long userId);

    /**
     * Retrieves root goals for a specific user, filtered by a specific status.
     *
     * @param userId   The owner user identifier.
     * @param status   The status enum value.
     * @return List of filtered root goals.
     */
    List<Goal> findByUserIdAndParentIsNullAndStatus(Long userId, GoalStatusEnum status);

    /**
     * Finds goals that are currently in WAITING status whose planned start date has arrived or passed,
     * intended for automatic activation by the nocturnal scheduler.
     *
     * @return List of goals ready to be set to IN_PROGRESS.
     */
    @Query("SELECT g FROM Goal g WHERE g.status = com.goal_tracker.goals.entity.GoalStatusEnum.WAITING AND g.startDate <= CURRENT_DATE")
    List<Goal> findGoalsReadyToStart();

    /**
     * Finds active goals (IN_PROGRESS) whose end date has passed,
     * intended for automatic failure processing by the nocturnal scheduler.
     *
     * @return List of goals that have expired.
     */
    @Query("SELECT g FROM Goal g WHERE g.status = com.goal_tracker.goals.entity.GoalStatusEnum.IN_PROGRESS AND g.endDate < CURRENT_DATE")
    List<Goal> findExpiredGoals();

    /**
     * Updates the status of a specific goal ensuring owner isolation.
     *
     * @param id     The goal identifier.
     * @param userId The owner user identifier.
     * @param status The new status enum to set.
     * @return The number of updated rows.
     */
    @Modifying
    @Query("UPDATE Goal g SET g.status = :status, g.updatedAt = CURRENT_TIMESTAMP WHERE g.id = :id AND g.userId = :userId")
    int updateGoalStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("status") GoalStatusEnum status);

    /**
     * Deletes a specific goal ensuring secure multi-tenant isolation against IDOR vulnerabilities.
     *
     * @param id     The goal identifier.
     * @param userId The owner user identifier.
     */
    void deleteByIdAndUserId(Long id, Long userId);
}