package com.goal_tracker.goals.service;

import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.exceptions.GoalEndDateViolationException;
import com.goal_tracker.goals.exceptions.GoalOwnershipException;
import com.goal_tracker.goals.exceptions.GoalParentNotFoundException;
import com.goal_tracker.goals.exceptions.GoalStartDateViolationException;
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .anyMatch(h -> h.getNotes() != null && h.getNotes().contains("SYSTEM_STATUS_CASCADED"));
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
}