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
import com.goal_tracker.goals.dto.GoalStatusHistoryResponseDto;
import com.goal_tracker.goals.dto.GoalTreeResponseDto;
import com.goal_tracker.goals.dto.GoalUpdateRequestDto;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.entity.GoalStatusHistory;
import com.goal_tracker.goals.entity.StatusTransitionCauseEnum;
import com.goal_tracker.goals.exceptions.GoalEndDateImmutableException;
import com.goal_tracker.goals.exceptions.GoalEndDateViolationException;
import com.goal_tracker.goals.exceptions.GoalNotFoundException;
import com.goal_tracker.goals.exceptions.GoalOwnershipException;
import com.goal_tracker.goals.exceptions.GoalParentNotFoundException;
import com.goal_tracker.goals.exceptions.GoalStartDateImmutableException;
import com.goal_tracker.goals.exceptions.GoalStartDateRequiredException;
import com.goal_tracker.goals.exceptions.GoalStartDateViolationException;
import com.goal_tracker.goals.exceptions.GoalStatusNotFoundException;
import com.goal_tracker.goals.exceptions.GoalTerminalStateException;
import com.goal_tracker.goals.exceptions.IllegalGoalTransitionException;
import com.goal_tracker.goals.mapper.GoalMapper;
import com.goal_tracker.goals.mapper.GoalStatusHistoryMapper;
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
    private final GoalStatusHistoryMapper goalStatusHistoryMapper;

    /**
     * Constructs a new {@code GoalService} with the required dependencies.
     *
     * @param goalRepository              the repository for managing goal persistence and database operations
     * @param goalStatusHistoryRepository the repository for managing goal status history persistence and database operations
     * @param goalMapper                  the mapper used for converting between goal entities and data transfer objects
     */
    public GoalService(GoalRepository goalRepository, GoalStatusHistoryRepository goalStatusHistoryRepository, GoalMapper goalMapper,  GoalStatusHistoryMapper goalStatusHistoryMapper) {
        super();
        this.goalRepository = goalRepository;
        this.goalStatusHistoryRepository = goalStatusHistoryRepository;
        this.goalMapper = goalMapper;
        this.goalStatusHistoryMapper = goalStatusHistoryMapper;
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

            validateUpwardHierarchyDates(parentGoal, dto.startDate(), dto.endDate());
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
    private void validateUpwardHierarchyDates(Goal parent, LocalDate subgoalStartDate, LocalDate subgoalEndDate) {
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

            goalStatusHistoryRepository.save
            (
                new GoalStatusHistory(
                    goal, 
                    GoalStatusEnum.IN_PROGRESS, 
                    StatusTransitionCauseEnum.USER, 
                    notes, 
                    OffsetDateTime.now()
                )
            );
            propagateInProgressDownwards(goal);

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
                    goalStatusHistoryRepository.save
                    (
                        new GoalStatusHistory(
                            child, 
                            GoalStatusEnum.IN_PROGRESS, 
                            StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                            null, 
                            OffsetDateTime.now()
                        )
                    );
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

    /**
     * Retrieves the complete hierarchical tree of a goal for a specific user.
     * <p>
     * It fetches the root goal along with its entire sub-goal tree using an optimized fetch graph,
     * validates that the goal exists, and enforces strict ownership security constraints.
     * </p>
     *
     * @param userId The unique identifier of the authenticated user requesting the goal tree.
     * @param goalId The unique identifier of the target root goal.
     * @return A hierarchical {@link GoalTreeResponseDto} representing the goal and its nested sub-goals.
     * @throws GoalNotFoundException  if the goal does not exist.
     * @throws GoalOwnershipException if the goal does not belong to the authenticated user.
     */
    @Transactional(readOnly = true)
    public GoalTreeResponseDto getGoalTree(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new GoalNotFoundException(goalId));

        if (!goal.getUserId().equals(userId)) {
            throw new GoalOwnershipException(goalId, userId);
        }

        Goal goalTree = goalRepository.findWithSubGoalsByIdAndUserId(goalId,userId).get();

        return goalMapper.toTreeResponse(goalTree);
    }

    /**
     * Retrieves the chronological timeline of status changes for a specific goal.
     * <p>
     * It validates that the goal exists, enforces strict ownership security constraints,
     * and fetches the status history records ordered by date.
     * </p>
     *
     * @param userId The unique identifier of the authenticated user owning the goal.
     * @param goalId The unique identifier of the target goal.
     * @return A list of {@link GoalStatusHistoryResponseDto} representing the status change timeline.
     * @throws GoalNotFoundException  if the goal does not exist.
     * @throws GoalOwnershipException if the goal does not belong to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<GoalStatusHistoryResponseDto> getGoalHistory(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new GoalNotFoundException(goalId));

        if (!goal.getUserId().equals(userId)) {
            throw new GoalOwnershipException(goalId, userId);
        }

        List<GoalStatusHistory> histories = goalStatusHistoryRepository
            .findByGoalIdAndUserIdRecentFirst(goalId, userId);

        return histories.stream()
            .map(goalStatusHistoryMapper::toResponse)
            .toList();
    }

    /**
     * Updates an existing goal's basic properties and dates, respecting multi-tenant boundaries,
     * upward/downward hierarchy date constraints, terminal state protections, nullable end dates, 
     * and strict immutable/required date rules per status.
     *
     * @param userId The unique identifier of the authenticated user performing the update.
     * @param goalId The unique identifier of the goal to update.
     * @param dto    The update payload containing the modified details.
     * @return An immutable {@link GoalResponseDto} representing the updated goal.
     * @throws GoalNotFoundException           if the goal does not exist.
     * @throws GoalOwnershipException          if the goal does not belong to the authenticated user.
     * @throws GoalTerminalStateException      if the goal has reached a terminal state and cannot be modified.
     * @throws GoalStartDateImmutableException if attempting to change the start date of an IN_PROGRESS goal.
     * @throws GoalStartDateRequiredException  if attempting to clear the start date of an IN_PROGRESS or PAUSED goal.
     * @throws GoalEndDateImmutableException   if attempting to modify or clear the end date of a PAUSED goal.
     */
    @Transactional
    public GoalResponseDto updateGoal(Long userId, Long goalId, GoalUpdateRequestDto dto) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new GoalNotFoundException(goalId));

        if (!goal.getUserId().equals(userId)) {
            throw new GoalOwnershipException(goalId, userId);
        }

        GoalStatusEnum currentStatus = goal.getStatus();

        if (currentStatus == GoalStatusEnum.COMPLETED || 
            currentStatus == GoalStatusEnum.FAILED || 
            currentStatus == GoalStatusEnum.DISCARDED) {
            throw new GoalTerminalStateException(goalId);
        }

        if (currentStatus == GoalStatusEnum.IN_PROGRESS) {
            if (dto.startDate() == null || !dto.startDate().equals(goal.getStartDate())) {
                throw new GoalStartDateImmutableException(goalId);
            }
        } else if (currentStatus == GoalStatusEnum.PAUSED) {
            if (dto.startDate() == null) {
                throw new GoalStartDateRequiredException(goalId);
            }
            if (dto.endDate() == null || !dto.endDate().equals(goal.getEndDate())) {
                throw new GoalEndDateImmutableException(goalId);
            }
        }

        if (goal.getParent() != null) {
            validateUpwardHierarchyDates(goal.getParent(), dto.startDate(), dto.endDate());
        }

        if (!goal.getSubGoals().isEmpty()) {
            validateDownwardHierarchyDates(goal, dto.startDate(), dto.endDate());
        }

        goal.setTitle(dto.title());
        goal.setDescription(dto.description());
        goal.setEndDate(dto.endDate());

        if (currentStatus == GoalStatusEnum.WAITING || currentStatus == GoalStatusEnum.PAUSED) {
            if (dto.startDate() != null && !dto.startDate().equals(goal.getStartDate())) {
                goal.setStartDate(dto.startDate());

                if (dto.startDate().isEqual(LocalDate.now())) {
                    goal.setStatus(GoalStatusEnum.IN_PROGRESS);
                    
                    goalStatusHistoryRepository.save(new GoalStatusHistory(
                        goal,
                        GoalStatusEnum.IN_PROGRESS,
                        StatusTransitionCauseEnum.USER,
                        "Goal activated via start date update to today",
                        OffsetDateTime.now()
                    ));

                    if (currentStatus == GoalStatusEnum.PAUSED) {
                        cascadeUnpauseToChildren(goal);
                    }
                    else if (currentStatus == GoalStatusEnum.WAITING){
                        propagateInProgressDownwards(goal);
                    }
                    if (goal.getParent() != null) {
                        propagateInProgressUpwards(goal.getParent(), goal.getId(), LocalDate.now());
                    }
                }
            }
        }

        Goal updatedGoal = goalRepository.save(goal);
        return goalMapper.toResponse(updatedGoal);
    }

    /**
     * Recursively traverses down the descendant chain (sub-goals) to validate temporal constraints:
     * - Parent startDate cannot be later than any descendant's startDate (parent cannot start after children).
     * - Parent endDate cannot be earlier than any descendant's endDate (parent cannot end before children).
     * 
     * @param goal               The parent goal being updated.
     * @param newParentStartDate The proposed new start date for the parent.
     * @param newParentEndDate   The proposed new end date for the parent.
     * @throws GoalStartDateViolationException if the new start date is after a descendant's start date.
     * @throws GoalEndDateViolationException   if the new end date is before a descendant's end date.
     */
    private void validateDownwardHierarchyDates(Goal goal, LocalDate newParentStartDate, LocalDate newParentEndDate) {
        for (Goal child : goal.getSubGoals()) {
            if (child.getStartDate() != null && newParentStartDate != null) {
                if (newParentStartDate.isAfter(child.getStartDate())) {
                    throw new GoalStartDateViolationException(child.getStartDate(), newParentStartDate);
                }
            }

            if (child.getEndDate() != null && newParentEndDate != null) {
                if (newParentEndDate.isBefore(child.getEndDate())) {
                    throw new GoalEndDateViolationException(child.getEndDate(), newParentEndDate);
                }
            }

            validateDownwardHierarchyDates(child, newParentStartDate, newParentEndDate);
        }
    }

    /**
     * Propagates the IN_PROGRESS status to all the descendants of a goal
     * @param goal goal that started the IN_PROGRESS propagation
     */
    private void propagateInProgressDownwards(Goal goal){
        if(!goal.getSubGoals().isEmpty()){
            for(Goal child : goal.getSubGoals()){
                if(child.getStatus()==GoalStatusEnum.WAITING){
                    child.setStartDate(LocalDate.now());
                    child.setStatus(GoalStatusEnum.IN_PROGRESS);
                    goalStatusHistoryRepository.save
                    (
                        new GoalStatusHistory
                        (
                            child, 
                            GoalStatusEnum.IN_PROGRESS, 
                            StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                            null,
                            OffsetDateTime.now()
                        )
                    );
                    goalRepository.save(child);

                    propagateInProgressDownwards(child);
                }
            }
        }
    }

    /**
     * Deletes an existing goal and its entire sub-goal tree recursively, respecting multi-tenant 
     * boundaries, ownership security, and database cascading rules.
     *
     * @param userId The unique identifier of the authenticated user performing the deletion.
     * @param goalId The unique identifier of the goal to delete.
     * @throws GoalNotFoundException  if the goal does not exist.
     * @throws GoalOwnershipException if the goal does not belong to the authenticated user.
     */
    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new GoalNotFoundException(goalId));

        if (!goal.getUserId().equals(userId)) {
            throw new GoalOwnershipException(goalId, userId);
        }

        Goal goalTree = goalRepository.findWithSubGoalsByIdAndUserId(goalId, userId)
            .orElse(goal);

        recursivelyDeleteGoalTree(goalTree);
    }

    /**
     * Helper recursive method to delete sub-goals in a bottom-up fashion (post-order traversal),
     * allowing Hibernate / database cascades to safely handle entity removal and history cleanup.
     *
     * @param goal The current goal node to process and delete.
     */
    private void recursivelyDeleteGoalTree(Goal goal) {
        if (goal.getSubGoals() != null && !goal.getSubGoals().isEmpty()) {
            List<Goal> childrenCopy = List.copyOf(goal.getSubGoals());
            for (Goal child : childrenCopy) {
                recursivelyDeleteGoalTree(child);
            }
        }
        
        if (goal.getParent() != null) {
            goal.getParent().getSubGoals().remove(goal);
            goalRepository.save( goal.getParent());
        }
        
        List<GoalStatusHistory> histories = goalStatusHistoryRepository
            .findByGoalIdAndUserIdRecentFirst(goal.getId(), goal.getUserId());
        if (histories != null && !histories.isEmpty()) {
            goalStatusHistoryRepository.deleteByGoalId(goal.getId());
        }

        goalRepository.deleteGoalCustomById(goal.getId());
    }
}