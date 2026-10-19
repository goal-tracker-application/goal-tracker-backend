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

        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStartDate()).isEqualTo(today);

        var histories = goalStatusHistoryRepository.findById(updatedParent.getId());
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


        var request = new GoalStatusChangeRequestDto(GoalStatusEnum.PAUSED.getId(), "Taking a break");

        var response = goalService.changeGoalStatus(testUser.getId(), parent.getId(), request);

        assertThat(response.statusId()).isEqualTo(GoalStatusEnum.PAUSED.getId());

        Goal updatedChild = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.PAUSED);

        GoalStatusHistory changeRegisterUpdatedChild = goalStatusHistoryRepository.findByGoalIdAndUserIdRecentFirst(updatedChild.getId(), updatedChild.getUserId()).getFirst();
        assertThat(changeRegisterUpdatedChild.getCause()).isEqualTo(StatusTransitionCauseEnum.DOWNWARD_CASCADE);

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
}