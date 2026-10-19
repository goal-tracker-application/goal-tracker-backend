package com.goal_tracker.goals.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.dto.GoalStatusChangeRequestDto;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.entity.GoalStatusHistory;
import com.goal_tracker.goals.entity.StatusTransitionCauseEnum;
import com.goal_tracker.goals.exceptions.GoalEndDateViolationException;
import com.goal_tracker.goals.exceptions.GoalNotFoundException;
import com.goal_tracker.goals.exceptions.GoalOwnershipException;
import com.goal_tracker.goals.exceptions.GoalParentNotFoundException;
import com.goal_tracker.goals.exceptions.GoalStartDateViolationException;
import com.goal_tracker.goals.exceptions.GoalStatusNotFoundException;
import com.goal_tracker.goals.exceptions.IllegalGoalTransitionException;
import com.goal_tracker.goals.mapper.GoalMapper;
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;

/**
 * Service handling business logic operations for goals management.
 */
@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalStatusHistoryRepository goalStatusHistoryRepository;
    private final GoalMapper goalMapper;

    /**
     * Constructs a new {@code GoalService} with the required dependencies.
     *
     * @param goalRepository              the repository for managing goal persistence and database operations
     * @param goalStatusHistoryRepository the repository for managing goal status history persistence and database operations
     * @param goalMapper                  the mapper used for converting between goal entities and data transfer objects
     */
    public GoalService(GoalRepository goalRepository, GoalStatusHistoryRepository goalStatusHistoryRepository, GoalMapper goalMapper) {
        super();
        this.goalRepository = goalRepository;
        this.goalStatusHistoryRepository = goalStatusHistoryRepository;
        this.goalMapper = goalMapper;
    }

    /**
     * Creates a new goal for a specific user under strict multi-tenant, date hierarchy, 
     * and status propagation rules.
     *
     * @param userId The identifier of the authenticated user creating the goal.
     * @param dto    The creation payload containing goal details.
     * @return The created goal exposed as an immutable response DTO.
     * @throws GoalParentNotFoundException If the goal parent was not found.
     * @throws GoalOwnershipException      If the goal parent was not owned by the current user.
     */
    @Transactional
    public GoalResponseDto createGoal(Long userId, GoalCreateRequestDto dto) {
        Goal parentGoal = null;

        if (dto.parentId() != null) {
            parentGoal = goalRepository.findById(dto.parentId())
                .orElseThrow(() -> new GoalParentNotFoundException(dto.parentId()));

            if (!parentGoal.getUserId().equals(userId)) {
                throw new GoalOwnershipException(parentGoal.getId(), userId);
            }

            validateHierarchyDates(parentGoal, dto.startDate(), dto.endDate());
        }

        Goal goal = goalMapper.toEntity(dto);
        goal.setUserId(userId);
        goal.setParent(parentGoal);

        GoalStatusEnum initialStatus = determineInitialStatus(dto.startDate());
        goal.setStatus(initialStatus);

        Goal savedGoal = goalRepository.save(goal);
        if(parentGoal!= null){
            parentGoal.getSubGoals().add(savedGoal);
            goalRepository.save(parentGoal);
        }

        if (initialStatus == GoalStatusEnum.IN_PROGRESS && parentGoal != null) {
            propagateInProgressUpwards(parentGoal, savedGoal.getId(), dto.startDate());
        }

        return goalMapper.toResponse(savedGoal);
    }

    /**
     * Recursively traverses up the ancestor chain to validate temporal constraints:
     * - Subgoal startDate cannot be earlier than an ancestor's startDate.
     * - Subgoal endDate cannot be later than an ancestor's endDate.
     * 
     * @throws GoalStartDateViolationException if the start date of the subgoal is before the start date of one of its ancestors.
     * @throws GoalEndDateViolationException   if the end date of the subgoal is after the end date of one of its ancestors.
     */
    private void validateHierarchyDates(Goal parent, LocalDate subgoalStartDate, LocalDate subgoalEndDate) {
        Goal current = parent;
        while (current != null) {
            if (current.getStartDate() != null && subgoalStartDate != null) {
                if (subgoalStartDate.isBefore(current.getStartDate())) {
                    throw new GoalStartDateViolationException(subgoalStartDate, current.getStartDate());
                }
                break;
            }
            current = current.getParent();
        }

        current = parent;
        while (current != null) {
            if (current.getEndDate() != null && subgoalEndDate != null) {
                if (subgoalEndDate.isAfter(current.getEndDate())) {
                    throw new GoalEndDateViolationException(subgoalEndDate, current.getEndDate());
                }
                break;
            }
            current = current.getParent();
        }
    }

    /**
     * Determines the initial status of a goal based on its start date.
     * Returns IN_PROGRESS if it starts today, WAITING otherwise.
     */
    private GoalStatusEnum determineInitialStatus(LocalDate startDate) {
        if (startDate != null && startDate.isEqual(LocalDate.now())) {
            return GoalStatusEnum.IN_PROGRESS;
        }
        return GoalStatusEnum.WAITING;
    }

   /**
     * Recursively propagates the IN_PROGRESS status and adjusts start/end dates upwards 
     * to parent goals that are currently in WAITING or PAUSED status, stopping when reaching 
     * an ancestor that is already IN_PROGRESS.
     *
     * @param parent         The starting parent that triggered the activation/unpausing.
     * @param childId        The numeric identifier of the triggering child goal.
     * @param childStartDate The start date of the triggering child goal.
     */
    private void propagateInProgressUpwards(Goal parent, Long childId, LocalDate childStartDate) {
        Goal current = parent;
        
        while (current != null && current.getStatus() != GoalStatusEnum.IN_PROGRESS) {

            GoalStatusEnum currentStatus = current.getStatus();

            if (currentStatus == GoalStatusEnum.WAITING) {
                current.setStatus(GoalStatusEnum.IN_PROGRESS);

                if (current.getStartDate() == null) {
                    current.setStartDate(childStartDate);
                }

                goalRepository.save(current);

                GoalStatusHistory history = new GoalStatusHistory(
                    current,
                    GoalStatusEnum.IN_PROGRESS,
                    StatusTransitionCauseEnum.UPWARD_CASCADE,
                    null,
                    OffsetDateTime.now()
                );
                goalStatusHistoryRepository.save(history);

            } else if (currentStatus == GoalStatusEnum.PAUSED) {
                GoalStatusHistory lastPauseRecord = goalStatusHistoryRepository
                    .findByGoalIdAndUserIdRecentFirst(current.getId(), current.getUserId())
                    .getFirst();

                long frozenDays = 0;
                if (lastPauseRecord != null) {
                    frozenDays = ChronoUnit.DAYS.between(
                        lastPauseRecord.getChangedAt().toLocalDate(), 
                        LocalDate.now()
                    );
                }

                current.setStartDate(LocalDate.now());
                if (current.getEndDate() != null) {
                    current.setEndDate(current.getEndDate().plusDays(frozenDays));
                }

                current.setStatus(GoalStatusEnum.IN_PROGRESS);
                goalRepository.save(current);

                GoalStatusHistory history = new GoalStatusHistory(
                    current,
                    GoalStatusEnum.IN_PROGRESS,
                    StatusTransitionCauseEnum.UPWARD_CASCADE,
                    null,
                    OffsetDateTime.now()
                );
                goalStatusHistoryRepository.save(history);
            }
            
            current = current.getParent();
        }
    }

    /**
     * Changes the status of a goal, handling state machine rules, elastic unpausing,
     * and upward/downward cascading behaviors.
     *
     * @param userId The ID of the user requesting the status change.
     * @param goalId The ID of the goal to update.
     * @param dto    The request DTO containing the target status and optional notes.
     * @return The updated goal response DTO.
     * @throws GoalNotFoundException           if the goal is not found.
     * @throws GoalOwnershipException          if the goal is not owned by the current user.
     * @throws IllegalGoalTransitionException  if the requested goal transition is illegal.
     * @throws GoalEndDateViolationException   if the end date of the subgoal is after the end date of an ancestor.
     */
    @Transactional
    public GoalResponseDto changeGoalStatus(Long userId, Long goalId, GoalStatusChangeRequestDto dto) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new GoalNotFoundException(goalId));

        if (!goal.getUserId().equals(userId)) {
            throw new GoalOwnershipException(goalId, userId); 
        }

        GoalStatusEnum currentStatus = goal.getStatus();
        GoalStatusEnum targetStatus = GoalStatusEnum.fromId(dto.statusId());

        if (currentStatus == GoalStatusEnum.COMPLETED || 
            currentStatus == GoalStatusEnum.FAILED || 
            currentStatus == GoalStatusEnum.DISCARDED) {
            throw new IllegalGoalTransitionException(goalId, currentStatus);
        }

        if (currentStatus == targetStatus) {
            throw new IllegalGoalTransitionException(goalId);
        }

        switch (targetStatus) {
            case IN_PROGRESS -> handleTransitionToInProgress(goal, currentStatus, dto.notes());
            case PAUSED -> handleTransitionToPaused(goal, StatusTransitionCauseEnum.USER, dto.notes());
            case COMPLETED, FAILED, DISCARDED -> handleTerminalTransition(goal, targetStatus, StatusTransitionCauseEnum.USER, dto.notes());
            case WAITING -> throw new IllegalGoalTransitionException(goalId, currentStatus, targetStatus);
        }

        Goal savedGoal = goalRepository.save(goal);
        return goalMapper.toResponse(savedGoal);
    }

    /**
     * Handles transitions towards IN_PROGRESS status (Activation or Elastic Unpausing).
     */
    private void handleTransitionToInProgress(Goal goal, GoalStatusEnum currentStatus, String notes) {
        if (currentStatus == GoalStatusEnum.WAITING) {
            goal.setStatus(GoalStatusEnum.IN_PROGRESS);
            goal.setStartDate(LocalDate.now());

            goalStatusHistoryRepository.save(new GoalStatusHistory(goal, GoalStatusEnum.IN_PROGRESS, StatusTransitionCauseEnum.USER, notes, OffsetDateTime.now()));

        } else if (currentStatus == GoalStatusEnum.PAUSED) {
            GoalStatusHistory lastPauseRecord = goalStatusHistoryRepository
                .findByGoalIdAndUserIdRecentFirst(goal.getId(), goal.getUserId())
                .getFirst();

            long frozenDays = ChronoUnit.DAYS.between(
                lastPauseRecord.getChangedAt().toLocalDate(), 
                LocalDate.now()
            );

            goal.setStartDate(LocalDate.now());
            if (goal.getEndDate() != null) {
                goal.setEndDate(goal.getEndDate().plusDays(frozenDays));
            }

            goal.setStatus(GoalStatusEnum.IN_PROGRESS);
            goalStatusHistoryRepository.save(new GoalStatusHistory(goal, GoalStatusEnum.IN_PROGRESS, StatusTransitionCauseEnum.USER, notes, OffsetDateTime.now()));

            cascadeUnpauseToChildren(goal);
        }

        propagateInProgressUpwards(goal.getParent(), goal.getId(), LocalDate.now());
    }

    /**
     * Handles mass pausing of a goal and its active sub-tree in cascade.
     */
    private void handleTransitionToPaused(Goal goal, StatusTransitionCauseEnum cause, String notes) {
        goal.setStatus(GoalStatusEnum.PAUSED);
        goalStatusHistoryRepository.save(
            new GoalStatusHistory
            (
                goal,
                GoalStatusEnum.PAUSED,
                StatusTransitionCauseEnum.USER, 
                notes,
                OffsetDateTime.now()
            )
        );
        cascadeaPauseToChildren(goal);
    }
    /**
     * Propagates the pause status from one goal to all its childrens
     * @param goal goal to cascade from the pasuse to its childrens
     */
    private void cascadeaPauseToChildren(Goal goal){
        for (Goal child : goal.getSubGoals()) {
            GoalStatusEnum childStatus = child.getStatus();
            if (childStatus == GoalStatusEnum.IN_PROGRESS || childStatus == GoalStatusEnum.WAITING) {
                child.setStatus(GoalStatusEnum.PAUSED);
                goalRepository.save(child);
                goalStatusHistoryRepository.save(
                    new GoalStatusHistory
                    (
                        child,
                        GoalStatusEnum.PAUSED,
                        StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                        null,
                        OffsetDateTime.now()
                    )
                );
                cascadeaPauseToChildren(child);
            }
        }
    }

    /**
     * Helper recursive method to cascade unpause down the sub-tree,
     * verifying via strongly-typed cause if the child was paused by a system cascade event.
     */
    private void cascadeUnpauseToChildren(Goal goal) {
        for (Goal child : goal.getSubGoals()) {
            if (child.getStatus() == GoalStatusEnum.PAUSED) {
                GoalStatusHistory lastChildPause = goalStatusHistoryRepository
                    .findByGoalIdAndUserIdRecentFirst(child.getId(), child.getUserId())
                    .getFirst();

                if (lastChildPause != null && lastChildPause.getCause() == StatusTransitionCauseEnum.DOWNWARD_CASCADE) {
                    
                    long childFrozenDays = ChronoUnit.DAYS.between(
                        lastChildPause.getChangedAt().toLocalDate(), 
                        LocalDate.now()
                    );

                    child.setStartDate(LocalDate.now());
                    if (child.getEndDate() != null) {
                        child.setEndDate(child.getEndDate().plusDays(childFrozenDays));
                    }

                    child.setStatus(GoalStatusEnum.IN_PROGRESS);
                    goalStatusHistoryRepository.save(new GoalStatusHistory(child, GoalStatusEnum.IN_PROGRESS, StatusTransitionCauseEnum.DOWNWARD_CASCADE, "Unpaused by parent cascade", OffsetDateTime.now()));
                    goalRepository.save(child);

                    cascadeUnpauseToChildren(child);
                }
            }
        }
    }

    /**
     * Handles terminal state transitions (COMPLETED, FAILED, DISCARDED) and downward cascade.
     */
    private void handleTerminalTransition(Goal goal, GoalStatusEnum targetStatus, StatusTransitionCauseEnum cause, String notes) {
        goal.setStatus(targetStatus);
        goalStatusHistoryRepository.save(new GoalStatusHistory(goal, targetStatus, cause, notes, OffsetDateTime.now()));

        cascadeTerminalToChildren(goal);
    }

    /**
     * Helper recursive method to cascade terminal closure down the sub-tree.
     */
    private void cascadeTerminalToChildren(Goal goal) {
        for (Goal child : goal.getSubGoals()) {
            GoalStatusEnum childStatus = child.getStatus();
            if (childStatus != GoalStatusEnum.COMPLETED && 
                childStatus != GoalStatusEnum.FAILED && 
                childStatus != GoalStatusEnum.DISCARDED) {
                
                child.setStatus(GoalStatusEnum.DISCARDED);
                goalStatusHistoryRepository.save(new GoalStatusHistory(child, GoalStatusEnum.DISCARDED, StatusTransitionCauseEnum.DOWNWARD_CASCADE, "Set into DISCARDED by parent closure cascade", OffsetDateTime.now()));
                goalRepository.save(child);
                
                cascadeTerminalToChildren(child);
            }
        }
    }

    /**
     * Retrieves root-level goals for a specific user, with an optional filter by status identifier.
     * <p>
     * If a status identifier is provided, it validates its existence through the goal status enumeration.
     * If the status identifier does not exist, a {@link GoalStatusNotFoundException} is thrown.
     * If no status identifier is provided, all root-level goals belonging to the user are returned.
     * </p>
     *
     * @param userId       The unique identifier of the authenticated user owning the goals.
     * @param goalStatusId Optional numeric identifier to filter root goals by status.
     * @return A list of {@link GoalResponseDto} representing the filtered root-level goals.
     * @throws GoalStatusNotFoundException if the provided status identifier is invalid or non-existent.
     */
    @Transactional(readOnly = true)
    public List<GoalResponseDto> getRootGoals(Long userId, Integer goalStatusId) {
        GoalStatusEnum status = null;

        if (goalStatusId != null) {
            status = GoalStatusEnum.fromId(goalStatusId);
            if(status==null){
                throw new GoalStatusNotFoundException(goalStatusId);
            }
        }

        List<Goal> rootGoals;
        if (status != null) {
            rootGoals = goalRepository.findByUserIdAndParentIsNullAndStatus(userId, status);
        } else {
            rootGoals = goalRepository.findByUserIdAndParentIsNull(userId);
        }

        return rootGoals.stream()
            .map(goalMapper::toResponse)
            .toList();
    }
}