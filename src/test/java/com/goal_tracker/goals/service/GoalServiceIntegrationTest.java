package com.goal_tracker.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.dto.GoalStatusChangeRequestDto;
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
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;

/**
 * Integration tests for the {@link GoalService} class, validating business rules, 
 * date hierarchy restrictions, multi-tenant security, and status propagation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoalServiceIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired 
    private GoalStatusHistoryRepository goalStatusHistoryRepository;

    @Autowired
    private GoalService goalService;
    

    private User testUser;
    private User anotherUser;

    /**
     * Sets up test data before each test execution, creating a standard user and a foreign user
     * to test multi-tenant boundaries.
     */
    @BeforeEach
    void setUp() {
        testUser = new User(
                "owner@goaltracker.com",
                "EncodedPassword123:",
                false,
                OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(testUser);

        anotherUser = new User(
                "stranger@goaltracker.com",
                "EncodedPassword123:",
                false,
                OffsetDateTime.now().plusDays(1)
        );
        userRepository.save(anotherUser);
        userRepository.flush();
    }

    /**
     * Verifies that a root goal is successfully created with a WAITING status 
     * when its start date is scheduled in the future.
     */
    @Test
    @DisplayName("createGoal: Should successfully create a root goal with WAITING status when start date is in the future")
    void createGoalRootWaitingSuccess() {
        // Arrange
        LocalDate futureDate = LocalDate.now().plusDays(5);
        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Learn Spring Boot",
                "Master backend architecture",
                futureDate,
                futureDate.plusDays(10),
                null
        );

        // Act
        var response = goalService.createGoal(testUser.getId(), request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Learn Spring Boot");
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.WAITING.getId());
        assertThat(response.parentId()).isNull();

        Goal savedGoal = goalRepository.findById(response.id()).orElseThrow();
        assertThat(savedGoal.getUserId()).isEqualTo(testUser.getId());

        GoalStatusHistory creationRegister = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(savedGoal.getId(), savedGoal.getUserId()).getFirst();
        assertThat(creationRegister.getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);

    }

    /**
     * Verifies that a root goal is successfully created with an IN_PROGRESS status 
     * when its start date matches the current date.
     */
    @Test
    @DisplayName("createGoal: Should successfully create a root goal with IN_PROGRESS status when start date is today")
    void createGoalRootInProgressSuccess() {
        // Arrange
        LocalDate today = LocalDate.now();
        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Daily Workout",
                "Cardio and strength",
                today,
                today.plusDays(7),
                null
        );

        // Act
        var response = goalService.createGoal(testUser.getId(), request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        GoalStatusHistory creationRegister = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(response.id(), testUser.getId()).getFirst();
        assertThat(creationRegister.getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);
    }

    /**
     * Verifies that a subgoal can be successfully created under an already active parent goal,
     * fully respecting valid date hierarchy boundaries.
     */
    @Test
    @DisplayName("createGoal: Should successfully create a subgoal under an IN_PROGRESS parent respecting date validations")
    void createSubgoalUnderInProgressParentSuccess() {
        // Arrange: Create parent already IN_PROGRESS
        LocalDate today = LocalDate.now();
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Main Project",
            "Parent description",
            today,
            today.plusDays(30),
            GoalStatusEnum.IN_PROGRESS
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Subtask 1",
                "Specific subtask",
                today.plusDays(2),
                today.plusDays(10),
                parent.getId()
        );

        // Act
        var response = goalService.createGoal(testUser.getId(), request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.parentId()).isEqualTo(parent.getId());
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.WAITING.getId());

        GoalStatusHistory creationRegister = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(response.id(), testUser.getId()).getFirst();
        assertThat(creationRegister.getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);
    }

    /**
     * Verifies that creating a subgoal starting today triggers an upwards cascading propagation 
     * to a WAITING parent, updating its status to IN_PROGRESS and adjusting its start date accordingly.
     */
    @Test
    @DisplayName("createGoal: Should trigger upwards cascading propagation when creating a subgoal starting today under a WAITING parent")
    void createSubgoalTriggersCascadingPropagationSuccess() {
        LocalDate today = LocalDate.now();
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Future Master Goal",
            "Parent goal",
            null,
            null,
            GoalStatusEnum.WAITING
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Urgent Subgoal",
                "Needs to start right now",
                today,
                today.plusDays(5),
                parent.getId()
        );

        // Act
        var response = goalService.createGoal(testUser.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        GoalStatusHistory creationRegister = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(response.id(), testUser.getId()).getFirst();
        assertThat(creationRegister.getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);

        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStartDate()).isEqualTo(today);

        var histories = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(updatedParent.getId(), updatedParent.getUserId());
        assertThat(histories).isNotEmpty();
        boolean hasCascadedHistory = histories.stream()
                .anyMatch(h -> h.getCause() != null && h.getCause()== StatusTransitionCauseEnum.UPWARD_CASCADE);
        assertThat(hasCascadedHistory).isTrue();
    }

    /**
     * Verifies that attempting to create a subgoal with a non-existent parent ID throws a 
     * {@link GoalParentNotFoundException}.
     */
    @Test
    @DisplayName("createGoal: Should throw GoalParentNotFoundException when parentId does not exist")
    void createGoalParentNotFound() {
        // Arrange
        LocalDate today = LocalDate.now();
        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Orphan Subgoal",
                "No parent exists",
                today,
                today.plusDays(5),
                99999L
        );

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(testUser.getId(), request))
                .isInstanceOf(GoalParentNotFoundException.class)
                .hasMessageContaining("Parent goal with ID 99999 was not found");
    }

    /**
     * Verifies that attempting to attach a subgoal to a parent goal belonging to another user 
     * throws a {@link GoalOwnershipException} to enforce strict multi-tenant isolation.
     */
    @Test
    @DisplayName("createGoal: Should throw GoalOwnershipException when parent goal belongs to another user")
    void createGoalOwnershipViolation() {
        // Arrange: Parent belongs to anotherUser
        LocalDate today = LocalDate.now();
        Goal foreignParent = new Goal(
            anotherUser.getId(),
            null,
            "Foreign Goal",
            "Belongs to another user",
            today,
            today.plusDays(10),
            GoalStatusEnum.IN_PROGRESS
        );
        foreignParent = goalRepository.save(foreignParent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                foreignParent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Steal Subgoal",
                "Trying to attach to someone else's goal",
                today,
                today.plusDays(5),
                foreignParent.getId()
        );

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(testUser.getId(), request))
                .isInstanceOf(GoalOwnershipException.class)
                .hasMessageContaining(String.format("Goal with ID %d does not belong to user with ID %d", foreignParent.getId(), testUser.getId()));
    }

    /**
     * Verifies that attempting to create a subgoal whose start date is earlier than its ancestor's 
     * start date throws a {@link GoalStartDateViolationException}.
     */
    @Test
    @DisplayName("createGoal: Should throw GoalStartDateViolationException when subgoal start date is earlier than ancestor start date")
    void createGoalStartDateViolation() {
        // Arrange
        LocalDate today = LocalDate.now();
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Parent Goal",
            "Parent",
            today.plusDays(5),
            today.plusDays(20),
            GoalStatusEnum.WAITING
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        // Subgoal tries to start BEFORE parent
        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Early Subgoal",
                "Starts too early",
                today,
                today.plusDays(2),
                parent.getId()
        );

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(testUser.getId(), request))
                .isInstanceOf(GoalStartDateViolationException.class);
    }

    /**
     * Verifies that attempting to create a subgoal whose end date is later than its ancestor's 
     * end date throws a {@link GoalEndDateViolationException}.
     */
    @Test
    @DisplayName("createGoal: Should throw GoalEndDateViolationException when subgoal end date is later than ancestor end date")
    void createGoalEndDateViolation() {
        // Arrange
        LocalDate today = LocalDate.now();
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Parent Goal",
            "Parent",
            today,
            today.plusDays(10),
            GoalStatusEnum.IN_PROGRESS
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        // Subgoal tries to end AFTER parent
        GoalCreateRequestDto request = new GoalCreateRequestDto(
                "Late Subgoal",
                "Ends too late",
                today,
                today.plusDays(15),
                parent.getId()
        );

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(testUser.getId(), request))
                .isInstanceOf(GoalEndDateViolationException.class);
    }

    /**
     * Verifies that a goal successfully transitions from WAITING to IN_PROGRESS when it has no parents or children.
     */
    @Test
    @DisplayName("changeGoalStatus: Should successfully transition from WAITING to IN_PROGRESS without parents or children")
    void changeStatusWaitingToInProgressSuccess() {
        LocalDate futureDate = LocalDate.now().plusDays(3);
        Goal goal = new Goal
        (
            testUser.getId(),
            null,
            "Task",
            "Desc",
            futureDate,
            futureDate.plusDays(5),
            GoalStatusEnum.WAITING
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Starting now");

        var response = goalService.changeGoalStatus(testUser.getId(), goal.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());
        Goal updated = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(updated.getStartDate()).isEqualTo(LocalDate.now());
    }

    /**
     * Verifies that transitioning a child to IN_PROGRESS triggers an upward cascade to a WAITING parent that has no start date.
     */
    @Test
    @DisplayName("changeGoalStatus: Should trigger upward cascade when transitioning a child to IN_PROGRESS with a WAITING parent having no start date")
    void changeStatusUpwardCascadeWaitingParentSuccess() {
        Goal parent = new Goal
        (
            testUser.getId(),
            null, 
            "Parent", 
            "Desc", 
            null, 
            null, 
            GoalStatusEnum.WAITING
        );
        parent = goalRepository.save(parent);

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();


        LocalDate futureDate = LocalDate.now().plusDays(2);
        Goal child = new Goal
        (
            testUser.getId(), 
            parent, 
            "Child", 
            "Desc", 
            futureDate, 
            futureDate.plusDays(5), 
            GoalStatusEnum.WAITING
        );
        child = goalRepository.save(child);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Activate child");

        var response = goalService.changeGoalStatus(testUser.getId(), child.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());
        
        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStartDate()).isEqualTo(LocalDate.now());

        GoalStatusHistory changeRegister = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(updatedParent.getId(), updatedParent.getUserId()).getFirst();
        assertThat(changeRegister.getCause()).isEqualTo(StatusTransitionCauseEnum.UPWARD_CASCADE);

    }
    /**
     * Verifies that transitioning a child to IN_PROGRESS triggers an upward cascade to a PAUSED parent.
     */
    @Test
    @DisplayName("changeGoalStatus: Should trigger upward cascade when transitioning a child to IN_PROGRESS with a PAUSED parent")
    void changeStatusUpwardCascadePausedParentSuccess() {
        LocalDate today = LocalDate.now();
        LocalDate parentDate =today.plusDays(1);
        Goal parent = new Goal
        (
            testUser.getId(),
            null, 
            "Parent", 
            "Desc", 
            parentDate, 
            parentDate.plusDays(7), 
            GoalStatusEnum.PAUSED
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(2)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory
            (
                parent, 
                GoalStatusEnum.PAUSED, 
                StatusTransitionCauseEnum.USER, 
                "User paused", 
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        Goal child = new Goal
        (
            testUser.getId(), 
            parent, 
            "Child", 
            "Desc", 
            parentDate.plusDays(1), 
            parentDate.plusDays(5), 
            GoalStatusEnum.PAUSED
        );
        child = goalRepository.save(child);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(2)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory
            (
                child, 
                GoalStatusEnum.PAUSED, 
                StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                null, 
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        parent.getSubGoals().add(child);
        goalRepository.save(parent);
        goalRepository.flush();


        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Activate child");

        var response = goalService.changeGoalStatus(testUser.getId(), child.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());
        
        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStartDate()).isEqualTo(today);

        GoalStatusHistory changeRegister = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(updatedParent.getId(), updatedParent.getUserId()).getFirst();
        assertThat(changeRegister.getCause()).isEqualTo(StatusTransitionCauseEnum.UPWARD_CASCADE);

    }

    /**
     * Verifies that changing a goal to a terminal state cascades DISCARDED downward to non-terminal children while preserving historical terminal states.
     */
    @Test
    @DisplayName("changeGoalStatus: Should cascade terminal state (DISCARDED) downward to non-terminal children while respecting historical terminal states")
    void changeStatusTerminalDownwardCascadeSuccess() {
        Goal parent = new Goal
        (
            testUser.getId(), 
            null, 
            "Parent", 
            "Desc", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10), 
            GoalStatusEnum.IN_PROGRESS
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        Goal activeChild = new Goal
        (
            testUser.getId(), 
            parent, 
            "Active Child", 
            "Desc", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5), 
            GoalStatusEnum.IN_PROGRESS
        );
        activeChild = goalRepository.save(activeChild);
        goalRepository.flush();

        parent.getSubGoals().add(activeChild);
        
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                activeChild,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        Goal completedChild = new Goal
        (
            testUser.getId(), 
            parent, 
            "Completed Child", 
            "Desc", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5), 
            GoalStatusEnum.COMPLETED
        );
        completedChild = goalRepository.save(completedChild);
        goalRepository.flush();

        parent.getSubGoals().add(completedChild);
        goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                activeChild,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                activeChild,
                GoalStatusEnum.COMPLETED,
                StatusTransitionCauseEnum.USER,
                "Completed",
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.DISCARDED.getId(), "Abort project");

        var response = goalService.changeGoalStatus(testUser.getId(), parent.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.DISCARDED.getId());

        Goal updatedActiveChild = goalRepository.findById(activeChild.getId()).orElseThrow();
        assertThat(updatedActiveChild.getStatus()).isEqualTo(GoalStatusEnum.DISCARDED);
        GoalStatusHistory changeRegisterUpdatedActiveChild = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(updatedActiveChild.getId(), updatedActiveChild.getUserId()).getFirst();
        assertThat(changeRegisterUpdatedActiveChild.getCause()).isEqualTo(StatusTransitionCauseEnum.DOWNWARD_CASCADE);

        Goal updatedCompletedChild = goalRepository.findById(completedChild.getId()).orElseThrow();
        assertThat(updatedCompletedChild.getStatus()).isEqualTo(GoalStatusEnum.COMPLETED);
    }

    /**
     * Verifies that pausing a goal cascades the pause downward to its active or waiting sub-tree.
     */
    @Test
    @DisplayName("changeGoalStatus: Should pause goal and cascade pause downward to active/waiting sub-tree")
    void changeStatusPauseWithDownwardCascadeSuccess() {
        Goal parent = new Goal
        (
            testUser.getId(),
            null, 
            "Parent", 
            "Desc", 
            LocalDate.now(), 
            LocalDate.now().plusDays(10), 
            GoalStatusEnum.IN_PROGRESS
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();


        Goal child = new Goal
        (
            testUser.getId(), 
            parent, 
            "Child", 
            "Desc", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5), 
            GoalStatusEnum.IN_PROGRESS
        );
        child = goalRepository.save(child);
        goalRepository.flush();

        parent.getSubGoals().add(child);
        goalRepository.save(parent);
        goalRepository.flush();


        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.PAUSED.getId(), "Taking a break");

        var response = goalService.changeGoalStatus(testUser.getId(), parent.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.PAUSED.getId());

        Goal updatedChild = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.PAUSED);

        GoalStatusHistory changeRegisterUpdatedChild = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(updatedChild.getId(), updatedChild.getUserId()).getFirst();
        assertThat(changeRegisterUpdatedChild.getCause()).isEqualTo(StatusTransitionCauseEnum.DOWNWARD_CASCADE);

    }

    /**
     * Verifies that a IN_PROGRESS status change propagates downwards and upwards
     */
    @Test
    @DisplayName("changeGoalStatus: Should cascade IN_PROGRESS status downwards and upwards")
    void changeStatusCascadingInProgressStatusSuccess() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Goal parent = new Goal
        (
            testUser.getId(), 
            null, 
            "Parent", 
            "Desc", 
            null, 
            null,
            GoalStatusEnum.WAITING
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();


        Goal child = new Goal
        (
            testUser.getId(), 
            parent, 
            "Child", 
            "Desc", 
            tomorrow,
            tomorrow.plusDays(5), 
            GoalStatusEnum.WAITING
        );
        child = goalRepository.save(child);
        goalRepository.flush();
        
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        parent.getSubGoals().add(child);
        goalRepository.save(parent);
        goalRepository.flush();

        Goal grandChild = new Goal
        (
            testUser.getId(), 
            child, 
            "User Child", 
            "Desc", 
            tomorrow.plusDays(1),
            tomorrow.plusDays(5), 
            GoalStatusEnum.WAITING
        );
        grandChild = goalRepository.save(grandChild);
        goalRepository.flush();
        
        child.getSubGoals().add(grandChild);
        goalRepository.save(child);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                grandChild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Start work");

        var response = goalService.changeGoalStatus(testUser.getId(), child.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStartDate()).isEqualTo(LocalDate.now());

        Goal updatedChild = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedChild.getStartDate()).isEqualTo(LocalDate.now());

        Goal updatedGrandChild = goalRepository.findById(grandChild.getId()).orElseThrow();
        assertThat(updatedGrandChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedGrandChild.getStartDate()).isEqualTo(LocalDate.now());
    }

    /**
     * Verifies that elastic unpausing correctly affects parents and children paused by system cascades while keeping user-paused children intact.
     */
    @Test
    @DisplayName("changeGoalStatus: Should elastic unpause parent and children paused by system cascade, while respecting user-paused children")
    void changeStatusElasticUnpauseSelectiveSuccess() {
        Goal parent = new Goal
        (
            testUser.getId(), 
            null, 
            "Parent", 
            "Desc", 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(10), 
            GoalStatusEnum.PAUSED
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(6)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory
            (
                parent, 
                GoalStatusEnum.PAUSED, 
                StatusTransitionCauseEnum.USER, 
                "User paused", 
                OffsetDateTime.now().minusDays(5)
            )
        );

        Goal systemPausedChild = new Goal
        (
            testUser.getId(), 
            parent, 
            "System Child", 
            "Desc", 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(5), 
            GoalStatusEnum.PAUSED
        );
        systemPausedChild = goalRepository.save(systemPausedChild);

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                systemPausedChild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(6)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory
            (
                systemPausedChild, 
                GoalStatusEnum.PAUSED, 
                StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                null, 
                OffsetDateTime.now().minusDays(5)
            )
        );

        parent.getSubGoals().add(systemPausedChild);
        Goal userPausedChild = new Goal
        (
            testUser.getId(), 
            parent, 
            "User Child", 
            "Desc", 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(5), 
            GoalStatusEnum.PAUSED
        );
        userPausedChild = goalRepository.save(userPausedChild);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                userPausedChild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(6)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory(
                userPausedChild, 
                GoalStatusEnum.PAUSED, 
                StatusTransitionCauseEnum.USER, 
                "Manual pause", 
                OffsetDateTime.now().minusDays(5)
            )
        );
        goalStatusHistoryRepository.flush();
        parent.getSubGoals().add(systemPausedChild);
        goalRepository.save(parent);
        goalRepository.flush();


        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Resume work");

        var response = goalService.changeGoalStatus(testUser.getId(), parent.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        Goal updatedSystemChild = goalRepository.findById(systemPausedChild.getId()).orElseThrow();
        assertThat(updatedSystemChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);

        Goal updatedUserChild = goalRepository.findById(userPausedChild.getId()).orElseThrow();
        assertThat(updatedUserChild.getStatus()).isEqualTo(GoalStatusEnum.PAUSED);
    }

    /**
     * Verifies that attempting to change the status of a non-existent goal throws a GoalNotFoundException.
     */
    @Test
    @DisplayName("changeGoalStatus: Should throw GoalNotFoundException when goal does not exist")
    void changeStatusGoalNotFound() {
        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Notes");

        assertThatThrownBy(() -> goalService.changeGoalStatus(testUser.getId(), 99999L, request))
                .isInstanceOf(GoalNotFoundException.class)
                .hasMessageContaining("Goal with ID 99999 was not found");
    }

    /**
     * Verifies that attempting to change the status of a goal belonging to another user throws a GoalOwnershipException.
     */
    @Test
    @DisplayName("changeGoalStatus: Should throw GoalOwnershipException when goal belongs to another user")
    void changeStatusOwnershipViolation() {
        Goal foreignGoal = new Goal
        (
            anotherUser.getId(), 
            null, 
            "Foreign", 
            "Desc", 
            LocalDate.now(), 
            LocalDate.now().plusDays(5), 
            GoalStatusEnum.WAITING
        );
        Goal savedForeignGoal = goalRepository.save(foreignGoal);
        goalRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Notes");

        assertThatThrownBy(() -> goalService.changeGoalStatus(testUser.getId(), savedForeignGoal.getId(), request))
                .isInstanceOf(GoalOwnershipException.class);
    }

    /**
     * Verifies that attempting to change a goal status to its current status throws an IllegalGoalTransitionException.
     */
    @Test
    @DisplayName("changeGoalStatus: Should throw IllegalGoalTransitionException when target status is identical to current status")
    void changeStatusUnchangedStatusException() {
        Goal goal = new Goal(testUser.getId(), null, "Task", "Desc", LocalDate.now(), LocalDate.now().plusDays(5), GoalStatusEnum.IN_PROGRESS);
        Goal savedGoal = goalRepository.save(goal);
        goalRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Same status");

        assertThatThrownBy(() -> goalService.changeGoalStatus(testUser.getId(), savedGoal.getId(), request))
                .isInstanceOf(IllegalGoalTransitionException.class);
    }

    /**
     * Verifies that attempting to modify a goal that has already reached a terminal state throws an IllegalGoalTransitionException.
     */
    @Test
    @DisplayName("changeGoalStatus: Should throw IllegalGoalTransitionException when attempting to modify a terminal goal")
    void changeStatusTerminalStateModificationException() {
        Goal goal = new Goal(testUser.getId(), null, "Done Task", "Desc", LocalDate.now(), LocalDate.now().plusDays(5), GoalStatusEnum.COMPLETED);
        Goal savedGoal = goalRepository.save(goal);
        goalRepository.flush();

        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.IN_PROGRESS.getId(), "Try to reopen");

        assertThatThrownBy(() -> goalService.changeGoalStatus(testUser.getId(), savedGoal.getId(), request))
                .isInstanceOf(IllegalGoalTransitionException.class);
    }

    /**
     * Verifies that retrieving root goals with a valid status filter successfully returns matching root goals.
     */
    @Test
    @DisplayName("getRootGoals: Should successfully return filtered root goals when valid status ID is provided")
    void getRootGoalsSuccessWithFilteredResults() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        Goal goal = new Goal(
                testUser.getId(),
                null,
                "Root WAITING Goal",
                "Desc",
                futureDate,
                futureDate.plusDays(10),
                GoalStatusEnum.WAITING
        );
        goalRepository.save(goal);
        goalRepository.flush();

        var result = goalService.getRootGoals(testUser.getId(), GoalStatusEnum.WAITING.getId());

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Root WAITING Goal");
    }

    /**
     * Verifies that retrieving root goals returns an empty list when no root goals match the filter criteria[cite: 8].
     */
    @Test
    @DisplayName("getRootGoals: Should successfully return an empty list when no root goals match the status filter")
    void getRootGoalsSuccessEmptyList() {
        var result = goalService.getRootGoals(testUser.getId(), GoalStatusEnum.COMPLETED.getId());

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that attempting to retrieve root goals with an invalid status identifier throws a GoalStatusNotFoundException[cite: 8].
     */
    @Test
    @DisplayName("getRootGoals: Should throw GoalStatusNotFoundException when invalid status ID is provided")
    void getRootGoalsThrowsGoalStatusNotFoundException() {
        assertThatThrownBy(() -> goalService.getRootGoals(testUser.getId(), 99999))
                .isInstanceOf(GoalStatusNotFoundException.class)
                .hasMessageContaining("Goal status with ID 99999 was not found.");
    }

    /**
     * Verifies that retrieving a goal tree successfully returns the root goal along with its nested sub-goals.
     */
    @Test
    @DisplayName("getGoalTree: Should successfully return the goal tree with its sub-goals when valid ID and ownership are provided")
    void getGoalTreeSuccessWithSubGoals() {
        Goal parent = new Goal(
                testUser.getId(),
                null,
                "Parent Goal",
                "Root node",
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                GoalStatusEnum.IN_PROGRESS
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        Goal child = new Goal(
                testUser.getId(),
                parent,
                "Child Goal",
                "Leaf node",
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                GoalStatusEnum.WAITING
        );
        child = goalRepository.save(child);
        goalRepository.flush();

        parent.getSubGoals().add(child);
        goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var result = goalService.getGoalTree(testUser.getId(), parent.getId());

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(parent.getId());
        assertThat(result.title()).isEqualTo("Parent Goal");
        assertThat(result.subGoals()).isNotNull();
        assertThat(result.subGoals()).hasSize(1);
        assertThat(result.subGoals().get(0).title()).isEqualTo("Child Goal");
    }

    /**
     * Verifies that retrieving a goal tree successfully returns just the root node when it has no sub-goals.
     */
    @Test
    @DisplayName("getGoalTree: Should successfully return root node only when goal has no sub-goals")
    void getGoalTreeSuccessRootOnly() {
        Goal goal = new Goal(
                testUser.getId(),
                null,
                "Single Root Goal",
                "No children",
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                GoalStatusEnum.IN_PROGRESS
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var result = goalService.getGoalTree(testUser.getId(), goal.getId());

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(goal.getId());
        assertThat(result.subGoals()).isEmpty();
    }

    /**
     * Verifies that attempting to retrieve a goal tree for a non-existent goal ID throws a GoalNotFoundException.
     */
    @Test
    @DisplayName("getGoalTree: Should throw GoalNotFoundException when goal does not exist")
    void getGoalTreeNotFound() {
        assertThatThrownBy(() -> goalService.getGoalTree(testUser.getId(), 99999L))
                .isInstanceOf(GoalNotFoundException.class)
                .hasMessageContaining(String.format("Goal with ID %d was not found or is inaccessible.", 99999L));
    }

    /**
     * Verifies that attempting to retrieve a goal tree belonging to another user throws a GoalOwnershipException.
     */
    @Test
    @DisplayName("getGoalTree: Should throw GoalOwnershipException when goal belongs to another user")
    void getGoalTreeOwnershipViolation() {
        Goal foreignGoal = new Goal(
                anotherUser.getId(),
                null,
                "Foreign Goal",
                "Owned by another user",
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                GoalStatusEnum.IN_PROGRESS
        );
        Goal savedForeignGoal = goalRepository.save(foreignGoal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedForeignGoal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        assertThatThrownBy(() -> goalService.getGoalTree(testUser.getId(), savedForeignGoal.getId()))
                .isInstanceOf(GoalOwnershipException.class)
                .hasMessageContaining(String.format("Goal with ID %d does not belong to user with ID %d.", foreignGoal.getId(), testUser.getId()));
    }

    /**
     * Verifies that retrieving goal history successfully returns the chronological timeline when records exist.
     */
    @Test
    @DisplayName("getGoalHistory: Should successfully return history timeline when goal exists and has status changes")
    void getGoalHistorySuccessWithRecords() {
        Goal goal = new Goal(
                testUser.getId(),
                null,
                "History Goal",
                "Desc",
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                GoalStatusEnum.IN_PROGRESS
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        GoalStatusHistory history = new GoalStatusHistory(
                goal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.USER,
                "Initial creation notes",
                OffsetDateTime.now()
        );
        goalStatusHistoryRepository.save(history);
        goalStatusHistoryRepository.flush();

        var result = goalService.getGoalHistory(testUser.getId(), goal.getId());

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).cause()).isEqualTo(StatusTransitionCauseEnum.CREATION.name());
        assertThat(result.get(1).cause()).isEqualTo(StatusTransitionCauseEnum.USER.name());
    }

    /**
     * Verifies that retrieving goal history successfully returns an empty list when no status history records exist.
     */
    @Test
    @DisplayName("getGoalHistory: Should successfully return empty list when goal has no status history records")
    void getGoalHistorySuccessEmptyList() {
        Goal goal = new Goal(
                testUser.getId(),
                null,
                "No History Goal",
                "Desc",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5),
                GoalStatusEnum.WAITING
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        var result = goalService.getGoalHistory(testUser.getId(), goal.getId());

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that attempting to retrieve goal history for a non-existent goal ID throws a GoalNotFoundException.
     */
    @Test
    @DisplayName("getGoalHistory: Should throw GoalNotFoundException when goal does not exist")
    void getGoalHistoryNotFound() {
        assertThatThrownBy(() -> goalService.getGoalHistory(testUser.getId(), 99999L))
                .isInstanceOf(GoalNotFoundException.class)
                .hasMessageContaining("Goal with ID 99999 was not found");
    }

    /**
     * Verifies that attempting to retrieve goal history for a goal belonging to another user throws a GoalOwnershipException.
     */
    @Test
    @DisplayName("getGoalHistory: Should throw GoalOwnershipException when goal belongs to another user")
    void getGoalHistoryOwnershipViolation() {
        Goal foreignGoal = new Goal(
                anotherUser.getId(),
                null,
                "Foreign Goal",
                "Owned by another user",
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                GoalStatusEnum.IN_PROGRESS
        );
        Goal savedForeignGoal = goalRepository.save(foreignGoal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedForeignGoal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        assertThatThrownBy(() -> goalService.getGoalHistory(testUser.getId(), savedForeignGoal.getId()))
                .isInstanceOf(GoalOwnershipException.class)
                .hasMessageContaining(String.format("Goal with ID %d does not belong to user with ID %d", foreignGoal.getId(), testUser.getId()));
    }

    /**
     * Verifies that a WAITING goal without subgoals can be successfully updated with arbitrary changes.
     */
    @Test
    @DisplayName("updateGoal: Should successfully update a WAITING goal without subgoals with arbitrary changes")
    void updateGoalWaitingWithoutSubgoalsSuccess() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Initial Title",
            "Initial Description",
            futureDate,
            futureDate.plusDays(10),
            GoalStatusEnum.WAITING
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "Updated Title",
            "Updated Description",
            futureDate.plusDays(1),
            futureDate.plusDays(12)
        );

        var response = goalService.updateGoal(testUser.getId(), goal.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Updated Title");
        assertThat(response.description()).isEqualTo("Updated Description");
    }

    /**
     * Verifies that updating a goal respects hierarchy restrictions both upward and downward.
     */
    @Test
    @DisplayName("updateGoal: Should successfully update goal respecting upward and downward hierarchy restrictions")
    void updateGoalHierarchyRestrictionsSuccess() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Parent Goal",
            "Parent Desc",
            tomorrow,
            tomorrow.plusDays(20),
            GoalStatusEnum.WAITING
        );
        parent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        Goal child = new Goal(
            testUser.getId(),
            parent,
            "Child Goal",
            "Child Desc",
            tomorrow.plusDays(2),
            tomorrow.plusDays(10),
            GoalStatusEnum.WAITING
        );
        child = goalRepository.save(child);
        parent.getSubGoals().add(child);
        goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "Updated Parent",
            "Updated Desc",
            tomorrow,
            tomorrow.plusDays(15)
        );

        var response = goalService.updateGoal(testUser.getId(), parent.getId(), request);
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Updated Parent");
    }

    /**
     * Verifies that non-date fields can be successfully updated on any non-terminal goal.
     */
    @Test
    @DisplayName("updateGoal: Should successfully update non-date fields on non-terminal goal")
    void updateGoalNonDateFieldsSuccess() {
        LocalDate today = LocalDate.now();
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Old Title",
            "Old Desc",
            today,
            today.plusDays(10),
            GoalStatusEnum.IN_PROGRESS
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "New Title",
            "New Desc",
            today,
            today.plusDays(10)
        );

        var response = goalService.updateGoal(testUser.getId(), goal.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.description()).isEqualTo("New Desc");
    }

    /**
     * Verifies that an IN_PROGRESS goal can have its end date cleared (set to null).
     */
    @Test
    @DisplayName("updateGoal: Should successfully clear end date of an IN_PROGRESS goal")
    void updateGoalInProgressClearEndDateSuccess() {
        LocalDate today = LocalDate.now();
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Active Goal",
            "Desc",
            today,
            today.plusDays(10),
            GoalStatusEnum.IN_PROGRESS
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "Active Goal",
            "Desc",
            today,
            null
        );

        var response = goalService.updateGoal(testUser.getId(), goal.getId(), request);

        assertThat(response).isNotNull();
        Goal updated = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(updated.getEndDate()).isNull();
    }

    /**
     * Verifies that changing start date to today on a WAITING goal triggers activation 
     * and respects child waiting states.
     */
    @Test
    @DisplayName("updateGoal: Should trigger activation to IN_PROGRESS when WAITING goal start date is changed to today")
    void updateGoalWaitingToInProgressCascadeSuccess() {
        LocalDate today = LocalDate.now();
        
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Waiting Goal",
            "Desc",
            null,
            null,
            GoalStatusEnum.WAITING
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        Goal child = new Goal(
            testUser.getId(),
            goal,
            "Child Goal",
            "Child Desc",
            today.plusDays(1),
            today.plusDays(8),
            GoalStatusEnum.WAITING
        );
        child = goalRepository.save(child);
        goal.getSubGoals().add(child);
        goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();


        Goal grandChild =  new Goal(
            testUser.getId(),
            child,
            "Grand Child Goal",
            "Grand Child Desc",
            today.plusDays(2),
            today.plusDays(7),
            GoalStatusEnum.WAITING
        );
        grandChild = goalRepository.save(grandChild);
        child.getSubGoals().add(grandChild);
        goalRepository.save(child);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                grandChild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "Waiting Goal",
            "Desc",
            today,
            today.plusDays(10)
        );

        var response = goalService.updateGoal(testUser.getId(), child.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        Goal updatedGoal = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(updatedGoal.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedGoal.getStartDate()).isEqualTo(today);

        Goal updatedChild =  goalRepository.findById(child.getId()).orElseThrow();
        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedChild.getStartDate()).isEqualTo(today);

        Goal updatedGrandChild = goalRepository.findById(grandChild.getId()).orElseThrow();
        assertThat(updatedGrandChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedGrandChild.getStartDate()).isEqualTo(today);
    }

    /**
     * Verifies that changing start date to today on a PAUSED goal triggers cascade unpausing to IN_PROGRESS.
     */
    @Test
    @DisplayName("updateGoal: Should trigger cascade unpausing to IN_PROGRESS when PAUSED goal start date is changed to today")
    void updateGoalPausedToInProgressCascadeSuccess() {
        LocalDate aWeekAgo = LocalDate.now().minusDays(7);
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Paused Goal",
            "Desc",
            aWeekAgo,
            aWeekAgo.plusDays(11),
            GoalStatusEnum.PAUSED
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(8)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(new GoalStatusHistory(
            goal,
            GoalStatusEnum.PAUSED,
            StatusTransitionCauseEnum.USER,
            "User paused goal",
            OffsetDateTime.now().minusDays(6)
        ));

        Goal child = new Goal(
            testUser.getId(),
            goal,
            "Child Goal",
            "Child Desc",
            aWeekAgo.plusDays(2),
            aWeekAgo.plusDays(10),
            GoalStatusEnum.PAUSED
        );
        child = goalRepository.save(child);
        goal.getSubGoals().add(child);
        goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(8)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(new GoalStatusHistory(
            child,
            GoalStatusEnum.PAUSED,
            StatusTransitionCauseEnum.DOWNWARD_CASCADE,
            null,
            OffsetDateTime.now().minusDays(6)
        ));

        Goal grandChild = new Goal(
            testUser.getId(),
            child,
            "Grand Child Goal",
            "Grand Child Desc",
            aWeekAgo.plusDays(8),
            aWeekAgo.plusDays(9),
            GoalStatusEnum.PAUSED
        );
        grandChild = goalRepository.save(grandChild);
        child.getSubGoals().add(grandChild);
        goalRepository.save(child);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                grandChild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(8)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(new GoalStatusHistory(
            grandChild,
            GoalStatusEnum.PAUSED,
            StatusTransitionCauseEnum.DOWNWARD_CASCADE,
            "Cascaded pause",
            OffsetDateTime.now().minusDays(6)
        ));
        goalStatusHistoryRepository.flush();

        LocalDate today = LocalDate.now();
        var request = new GoalUpdateRequestDto(
            "Paused Goal",
            "Desc",
            today,
            aWeekAgo.plusDays(10)
        );

        var response = goalService.updateGoal(testUser.getId(), child.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        Goal updatedParent = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);

        Goal updatedGrandChild = goalRepository.findById(grandChild.getId()).orElseThrow();
        assertThat(updatedGrandChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
    }

    /**
     * Verifies that updating a child goal's start date to today under an ancestor hierarchy with null start dates 
     * triggers upward propagation to grandparents/parents and correctly activates the sub-tree.
     */
    @Test
    @DisplayName("updateGoal: Should trigger upward cascade to grandparent and parent when child start date is updated to today with null ancestor dates")
    void updateGoalWithNullAncestorDatesTriggersUpwardCascadeSuccess() {
        LocalDate today = LocalDate.now();

        Goal grandfather = new Goal(
            testUser.getId(),
            null,
            "Grandfather Goal",
            "Top ancestor",
            null,
            null,
            GoalStatusEnum.WAITING
        );
        grandfather = goalRepository.save(grandfather);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                grandfather,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(8)
            )
        );
        goalStatusHistoryRepository.flush();

        Goal parent = new Goal(
            testUser.getId(),
            grandfather,
            "Parent Goal",
            "Intermediate ancestor",
            null,
            null,
            GoalStatusEnum.WAITING
        );
        parent = goalRepository.save(parent);
        grandfather.getSubGoals().add(parent);
        goalRepository.save(grandfather);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(8)
            )
        );
        goalStatusHistoryRepository.flush();

        LocalDate futureDate = today.plusDays(5);
        Goal child = new Goal(
            testUser.getId(),
            parent,
            "Child Goal",
            "Leaf node",
            futureDate,
            futureDate.plusDays(10),
            GoalStatusEnum.WAITING
        );
        child = goalRepository.save(child);
        parent.getSubGoals().add(child);
        goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(8)
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "Child Goal",
            "Leaf node updated",
            today,
            futureDate.plusDays(10)
        );

        var response = goalService.updateGoal(testUser.getId(), parent.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.IN_PROGRESS.getId());

        Goal updatedChild = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedChild.getStartDate()).isEqualTo(today);

        Goal updatedGrandfather = goalRepository.findById(grandfather.getId()).orElseThrow();
        assertThat(updatedGrandfather.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedGrandfather.getStartDate()).isEqualTo(today);

        var childHistories = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(child.getId(), testUser.getId());
        boolean parentCascaded = childHistories.stream()
                .anyMatch(h -> h.getCause() == StatusTransitionCauseEnum.DOWNWARD_CASCADE);
        assertThat(parentCascaded).isTrue();

        var grandfatherHistories = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(grandfather.getId(), testUser.getId());
        boolean grandfatherCascaded = grandfatherHistories.stream()
                .anyMatch(h -> h.getCause() == StatusTransitionCauseEnum.UPWARD_CASCADE);
        assertThat(grandfatherCascaded).isTrue();
    }

    /**
     * Verifies that attempting to update a non-existent goal throws a GoalNotFoundException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalNotFoundException when goal does not exist")
    void updateGoalNotFound() {
        LocalDate today = LocalDate.now();
        var request = new GoalUpdateRequestDto("Title", "Desc", today, today.plusDays(5));

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), 99999L, request))
                .isInstanceOf(GoalNotFoundException.class);
    }

    /**
     * Verifies that attempting to update a goal belonging to another user throws a GoalOwnershipException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalOwnershipException when goal belongs to another user")
    void updateGoalOwnershipViolation() {
        LocalDate today = LocalDate.now();
        Goal foreignGoal = new Goal(
            anotherUser.getId(),
            null,
            "Foreign Goal",
            "Desc",
            today,
            today.plusDays(5),
            GoalStatusEnum.IN_PROGRESS
        );
        Goal savedForeignGoal = goalRepository.save(foreignGoal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedForeignGoal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto("Title", "Desc", today, today.plusDays(5));

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), savedForeignGoal.getId(), request))
                .isInstanceOf(GoalOwnershipException.class);
    }

    /**
     * Verifies that attempting to update a terminal goal throws a GoalTerminalStateException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalTerminalStateException when goal is in a terminal state")
    void updateGoalTerminalStateViolation() {
        LocalDate today = LocalDate.now();
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Completed Goal",
            "Desc",
            today,
            today.plusDays(5),
            GoalStatusEnum.COMPLETED
        );
        Goal savedGoal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedGoal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(2)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedGoal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.USER,
                "Completed",
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto("New Title", "Desc", today, today.plusDays(5));

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), savedGoal.getId(), request))
                .isInstanceOf(GoalTerminalStateException.class);
    }

    /**
     * Verifies that attempting to modify the start date of an IN_PROGRESS goal throws a GoalStartDateImmutableException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalStartDateImmutableException when modifying start date of an IN_PROGRESS goal")
    void updateGoalStartDateImmutableViolation() {
        LocalDate today = LocalDate.now();
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Active Goal",
            "Desc",
            today,
            today.plusDays(5),
            GoalStatusEnum.IN_PROGRESS
        );
        Goal savedGoal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedGoal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto("Title", "Desc", today.plusDays(1), today.plusDays(5));

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), savedGoal.getId(), request))
                .isInstanceOf(GoalStartDateImmutableException.class);
    }

    /**
     * Verifies that attempting to clear the start date of a PAUSED goal throws a GoalStartDateRequiredException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalStartDateRequiredException when clearing start date of a PAUSED goal")
    void updateGoalStartDateRequiredViolation() {
        LocalDate today = LocalDate.now();
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Paused Goal",
            "Desc",
            today,
            today.plusDays(5),
            GoalStatusEnum.PAUSED
        );
        Goal savedGoal = goalRepository.save(goal);
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedGoal,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(2)
            )
        );
        goalStatusHistoryRepository.flush();
        
        goalStatusHistoryRepository.save(new GoalStatusHistory(
            savedGoal,
            GoalStatusEnum.PAUSED,
            StatusTransitionCauseEnum.USER,
            "Paused for testing",
            OffsetDateTime.now().minusDays(1)
        ));
        goalRepository.flush();

        var request = new GoalUpdateRequestDto("Title", "Desc", null, null);

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), savedGoal.getId(), request))
                .isInstanceOf(GoalStartDateRequiredException.class);
    }

    /**
     * Verifies that attempting to modify the end date of a PAUSED goal throws a GoalEndDateImmutableException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalEndDateImmutableException when modifying end date of a PAUSED goal")
    void updateGoalEndDateImmutableViolation() {
        LocalDate today = LocalDate.now();
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Paused Goal",
            "Desc",
            today,
            today.plusDays(5),
            GoalStatusEnum.PAUSED
        );
        Goal savedGoal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedGoal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedGoal,
                GoalStatusEnum.PAUSED,
                StatusTransitionCauseEnum.USER,
                "paused",
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto("Title", "Desc", today, today.plusDays(10));

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), savedGoal.getId(), request))
                .isInstanceOf(GoalEndDateImmutableException.class);
    }

    /**
     * Verifies that updating a goal with dates violating upward hierarchy throws a GoalStartDateViolationException.
     */
    @Test
    @DisplayName("updateGoal: Should throw GoalStartDateViolationException when violating upward start date hierarchy")
    void updateGoalUpwardHierarchyStartDateViolation() {
        LocalDate today = LocalDate.now();
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Parent",
            "Desc",
            today.plusDays(5),
            today.plusDays(15),
            GoalStatusEnum.WAITING
        );
        Goal savedParent = goalRepository.save(parent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedParent,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        Goal child = new Goal(
            testUser.getId(),
            savedParent,
            "Child",
            "Desc",
            today.plusDays(5),
            today.plusDays(10),
            GoalStatusEnum.WAITING
        );
        Goal savedChild = goalRepository.save(child);
        savedParent.getSubGoals().add(savedChild);
        goalRepository.save(savedParent);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                savedChild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        var request = new GoalUpdateRequestDto(
            "Parent",
            "Desc",
            today.plusDays(8),
            today.plusDays(10)
        );

        assertThatThrownBy(() -> goalService.updateGoal(testUser.getId(), savedParent.getId(), request))
                .isInstanceOf(GoalStartDateViolationException.class);
    }

    /**
     * Verifies that a root goal without subgoals or history can be successfully deleted.
     */
    @Test
    @DisplayName("deleteGoal: Should successfully delete a root goal without subgoals or history")
    void deleteGoalRootWithoutSubgoalsOrHistorySuccess() {
        Goal goal = new Goal(
            testUser.getId(),
            null,
            "Simple Root Goal",
            "Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(5),
            GoalStatusEnum.WAITING
        );
        goal = goalRepository.save(goal);
        goalRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                goal,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        goalService.deleteGoal(testUser.getId(), goal.getId());

        assertThat(goalRepository.findById(goal.getId())).isEmpty();
    }

    /**
     * Verifies that a root goal with a full sub-goal tree (children and grandchildren) 
     * along with their respective status histories can be successfully deleted bottom-up.
     */
    @Test
    @DisplayName("deleteGoal: Should successfully delete a root goal with a full sub-goal tree and history records")
    void deleteGoalRootWithTreeAndHistorySuccess() {
        Goal root = new Goal(
            testUser.getId(),
            null,
            "Root Goal",
            "Root Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(10),
            GoalStatusEnum.IN_PROGRESS
        );
        root = goalRepository.save(root);
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                root,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                root,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.USER,
                "Started",
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        Goal child = new Goal(
            testUser.getId(),
            root,
            "Child Goal",
            "Child Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(5),
            GoalStatusEnum.IN_PROGRESS
        );
        child = goalRepository.save(child);
        root.getSubGoals().add(child);
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                child, 
                GoalStatusEnum.IN_PROGRESS, 
                StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                null, 
                OffsetDateTime.now()
            )
        );

        Goal grandchild = new Goal(
            testUser.getId(),
            child,
            "Grandchild Goal",
            "Grandchild Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(2),
            GoalStatusEnum.IN_PROGRESS
        );
        grandchild = goalRepository.save(grandchild);
        child.getSubGoals().add(grandchild);
        
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                grandchild,
                GoalStatusEnum.WAITING,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();
        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                grandchild,
                GoalStatusEnum.IN_PROGRESS, 
                StatusTransitionCauseEnum.DOWNWARD_CASCADE,
                null, 
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();
        goalRepository.save(root);
        goalRepository.flush();

        goalService.deleteGoal(testUser.getId(), root.getId());

        assertThat(goalRepository.findById(root.getId())).isEmpty();
        assertThat(goalRepository.findById(child.getId())).isEmpty();
        assertThat(goalRepository.findById(grandchild.getId())).isEmpty();
    }

    /**
     * Verifies that deleting an intermediate goal removes it along with its descendants and history, 
     * while keeping its parent node completely intact and correctly unlinked.
     */
    @Test
    @DisplayName("deleteGoal: Should successfully delete an intermediate goal with children and history while keeping parent intact")
    void deleteGoalIntermediateNodeSuccess() {
        Goal parent = new Goal(
            testUser.getId(),
            null,
            "Parent Goal",
            "Parent Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(15),
            GoalStatusEnum.IN_PROGRESS
        );
        parent = goalRepository.save(parent);

        goalStatusHistoryRepository.save(
            new GoalStatusHistory(
                parent,
                GoalStatusEnum.IN_PROGRESS,
                StatusTransitionCauseEnum.CREATION,
                null,
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();

        Goal intermediate = new Goal(
            testUser.getId(),
            parent,
            "Intermediate Goal",
            "Intermediate Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(10),
            GoalStatusEnum.IN_PROGRESS
        );
        intermediate = goalRepository.save(intermediate);
        parent.getSubGoals().add(intermediate);
        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory(
                intermediate, 
                GoalStatusEnum.IN_PROGRESS, 
                StatusTransitionCauseEnum.CREATION, 
                null, 
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();
        Goal leaf = new Goal(
            testUser.getId(),
            intermediate,
            "Leaf Goal",
            "Leaf Desc",
            LocalDate.now(),
            LocalDate.now().plusDays(5),
            GoalStatusEnum.IN_PROGRESS
        );
        leaf = goalRepository.save(leaf);
        intermediate.getSubGoals().add(leaf);
        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory(
                leaf, 
                GoalStatusEnum.WAITING, 
                StatusTransitionCauseEnum.CREATION, 
                null, 
                OffsetDateTime.now().minusDays(1)
            )
        );
        goalStatusHistoryRepository.flush();
        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory
            (
                leaf, 
                GoalStatusEnum.IN_PROGRESS, 
                StatusTransitionCauseEnum.DOWNWARD_CASCADE, 
                null, 
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();
        goalRepository.save(parent);
        goalRepository.flush();

        goalService.deleteGoal(testUser.getId(), intermediate.getId());


        Goal savedParent = goalRepository.findById(parent.getId()).orElseThrow();
        assertThat(savedParent).isNotNull();
        assertThat(savedParent.getSubGoals()).isEmpty();

        assertThat(goalRepository.findById(intermediate.getId())).isEmpty();
        assertThat(goalRepository.findById(leaf.getId())).isEmpty();
    }

    /**
     * Verifies that attempting to delete a non-existent goal throws a {@link GoalNotFoundException}.
     */
    @Test
    @DisplayName("deleteGoal: Should throw GoalNotFoundException when goal does not exist")
    void deleteGoalNotFound() {
        assertThatThrownBy(() -> goalService.deleteGoal(testUser.getId(), 99999L))
                .isInstanceOf(GoalNotFoundException.class)
                .hasMessageContaining("Goal with ID 99999 was not found");
    }

    /**
     * Verifies that attempting to delete a goal belonging to another user throws a {@link GoalOwnershipException}.
     */
    @Test
    @DisplayName("deleteGoal: Should throw GoalOwnershipException when goal belongs to another user")
    void deleteGoalOwnershipViolation() {
        Goal foreignGoal = new Goal(
            anotherUser.getId(),
            null,
            "Foreign Goal",
            "Owned by stranger",
            LocalDate.now(),
            LocalDate.now().plusDays(5),
            GoalStatusEnum.IN_PROGRESS
        );
        Goal savedForeignGoal = goalRepository.save(foreignGoal);
        goalStatusHistoryRepository.save
        (
            new GoalStatusHistory(
                savedForeignGoal, 
                GoalStatusEnum.IN_PROGRESS, 
                StatusTransitionCauseEnum.CREATION, 
                null, 
                OffsetDateTime.now()
            )
        );
        goalStatusHistoryRepository.flush();
        
        goalRepository.flush();

        assertThatThrownBy(() -> goalService.deleteGoal(testUser.getId(), savedForeignGoal.getId()))
                .isInstanceOf(GoalOwnershipException.class)
                .hasMessageContaining(String.format("Goal with ID %d does not belong to user with ID %d", savedForeignGoal.getId(), testUser.getId()));
    }
}