package com.goal_tracker.goals.repository;

import com.goal_tracker.goals.entity.GoalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for managing {@link GoalStatusHistory} audit entries.
 * <p>
 * Provides timeline retrieval capabilities, secure multi-tenant filtering,
 * and historical data extraction for pause-duration calculations.
 * </p>
 */
@Repository
public interface GoalStatusHistoryRepository extends JpaRepository<GoalStatusHistory, Long> {

    /**
     * Retrieves the complete status change history for a specific goal, ensuring owner isolation,
     * ordered chronologically from oldest to newest to match the timeline UI.
     *
     * @param goalId The unique identifier of the goal.
     * @param userId The owner user identifier.
     * @return List of history transition entries.
     */
    @Query("SELECT h FROM GoalStatusHistory h WHERE h.goal.id = :goalId AND h.goal.userId = :userId ORDER BY h.changedAt ASC")
    List<GoalStatusHistory> findByGoalIdAndUserIdOrdered(@Param("goalId") Long goalId, @Param("userId") Long userId);

    /**
     * Deletes all history entries associated with a specific goal, ensuring owner isolation.
     *
     * @param goalId The unique identifier of the goal.
     * @param userId The owner user identifier.
     */
    @Query("DELETE FROM GoalStatusHistory h WHERE h.goal.id = :goalId AND h.goal.userId = (SELECT g.userId FROM Goal g WHERE g.id = :goalId AND g.userId = :userId)")
    void deleteByGoalIdAndUserId(@Param("goalId") Long goalId, @Param("userId") Long userId);

    /**
     * Retrieves the most recent history entries for a specific goal, useful for finding
     * the exact moment a goal was paused to calculate paused durations.
     *
     * @param goalId The unique identifier of the goal.
     * @param userId The owner user identifier.
     * @return List of history entries ordered from newest to oldest.
     */
    @Query("SELECT h FROM GoalStatusHistory h WHERE h.goal.id = :goalId AND h.goal.userId = :userId ORDER BY h.changedAt DESC")
    List<GoalStatusHistory> findByGoalIdAndUserIdRecentFirst(@Param("goalId") Long goalId, @Param("userId") Long userId);

    /**
     * Deletes all status history records associated with a specific goal by its unique identifier.
     * Uses a bulk delete query to bypass entity lifecycle states and prevent transient persistence exceptions.
     *
     * @param goalId the numeric identifier of the goal whose history records are to be deleted
     */
    @Modifying
    @Query("DELETE FROM GoalStatusHistory h WHERE h.goal.id = :goalId")
    void deleteByGoalId(@Param("goalId") Long goalId);
}