package com.goal_tracker.goals.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.entity.GoalStatusHistory;
import com.goal_tracker.goals.exceptions.GoalEndDateViolationException;
import com.goal_tracker.goals.exceptions.GoalOwnershipException;
import com.goal_tracker.goals.exceptions.GoalParentNotFoundException;
import com.goal_tracker.goals.exceptions.GoalStartDateViolationException;
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
     * @param goalRepository the repository for managing goal persistence and database operations
     * @param goalStatusHistoryRepository the repository for managing goal status history persistence and database operations
     * @param goalMapper     the mapper used for converting between goal entities and data transfer objects
     */
    public GoalService(GoalRepository goalRepository,GoalStatusHistoryRepository goalStatusHistoryRepository,  GoalMapper goalMapper) {
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

        if (initialStatus == GoalStatusEnum.IN_PROGRESS && parentGoal != null) {
            propagateInProgressUpwards(parentGoal, savedGoal.getId(), dto.startDate());
        }

        return goalMapper.toResponse(savedGoal);
    }

    /**
     * Recursively traverses up the ancestor chain to validate temporal constraints:
     * - Subgoal startDate cannot be earlier than an ancestor's startDate.
     * - Subgoal endDate cannot be later than an ancestor's endDate.
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
     * Recursively propagates the IN_PROGRESS status and adjusts start dates upwards 
     * to parent goals that are currently in WAITING status, stopping when reaching 
     * an ancestor that is already IN_PROGRESS.
     *
     * @param goal              The starting goal that triggered the activation.
     * @param childId           The numeric identifier of the triggering child goal
     * @param childStartDate    The start date of the triggering child goal.
     */
    private void propagateInProgressUpwards(Goal goal, Long childId, LocalDate childStartDate ) {
        Goal current = goal;
        
        while (current != null && current.getStatus() == GoalStatusEnum.WAITING) {

            current.setStatus(GoalStatusEnum.IN_PROGRESS);

            if (current.getStartDate() == null) {
                current.setStartDate(childStartDate);
            }

            goalRepository.save(current);

            GoalStatusHistory history = new GoalStatusHistory(
                current,
                GoalStatusEnum.IN_PROGRESS,
                "SYSTEM_STATUS_CASCADED: Subgoal #" + childId+ " set into IN_PROGRESS",
                OffsetDateTime.now()
            );
            goalStatusHistoryRepository.save(history);
            
            current = current.getParent();
        }
    }
}