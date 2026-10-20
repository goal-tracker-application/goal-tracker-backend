package com.goal_tracker.goals.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;
import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.entity.GoalStatusHistory;
import com.goal_tracker.goals.entity.StatusTransitionCauseEnum;
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;

import jakarta.persistence.EntityManager;

/**
 * Integration test suite for {@link GoalActivationJob}, verifying automated
 * goal activation logic, cascading rules, and scheduling boundary constraints.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoalActivationJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GoalActivationJob goalActivationJob;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private GoalStatusHistoryRepository goalStatusHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;

    /**
     * Sets up test data before each test execution, creating a standard user.
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
        userRepository.flush();
    }


    /**
     * Tests that a single root goal in WAITING status with a past or current start date
     * is successfully activated into IN_PROGRESS with an AUTO_ACTIVATION history record.
     */
    @Test
    @DisplayName("Should activate single root goal when start date is today or past")
    void runGoalActivation_SingleRootGoal_Success() {
        // Arrange
        Goal rootGoal = createAndSaveGoalWithHistory(
            "Single Root Goal", 
            GoalStatusEnum.WAITING, 
            LocalDate.now(), 
            LocalDate.now().plusDays(10), 
            null
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalActivationJob.runGoalActivation();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedGoal = goalRepository.findById(rootGoal.getId()).orElseThrow();
        assertThat(updatedGoal.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);

        List<GoalStatusHistory> history = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(rootGoal.getId(), testUser.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);
        assertThat(history.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.AUTO_ACTIVATION);
        assertThat(history.get(1).getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
    }

    /**
     * Tests that activating a parent goal correctly propagates the IN_PROGRESS status
     * downwards to its waiting children via DOWNWARD_CASCADE.
     */
    @Test
    @DisplayName("Should activate parent goal and propagate IN_PROGRESS downwards to waiting children")
    void runGoalActivation_ParentGoal_PropagatesDownwards() {
        // Arrange
        Goal parent = createAndSaveGoalWithHistory(
            "Parent Goal", 
            GoalStatusEnum.WAITING, 
            LocalDate.now(), 
            LocalDate.now().plusDays(15), 
            null
        );

        Goal child1 = createAndSaveGoalWithHistory(
            "Child Goal 1", 
            GoalStatusEnum.WAITING, 
            LocalDate.now().plusDays(2), 
            LocalDate.now().plusDays(10), 
            parent
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalActivationJob.runGoalActivation();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        Goal updatedChild = goalRepository.findById(child1.getId()).orElseThrow();

        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);

        List<GoalStatusHistory> parentHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(parent.getId(), testUser.getId());
        assertThat(parentHistory.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.AUTO_ACTIVATION);

        List<GoalStatusHistory> childHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(child1.getId(), testUser.getId());
        assertThat(childHistory.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.DOWNWARD_CASCADE);
    }

    /**
     * Tests that activating a child goal successfully triggers upward propagation
     * to a flexible parent goal with null dates, updating its start date and setting it to IN_PROGRESS.
     */
    @Test
    @DisplayName("Should activate child goal and propagate upwards to parent with null dates")
    void runGoalActivation_ChildGoalWithNullDateParent_PropagatesUpwards() {
        // Arrange
        Goal parentWithNullDates = createAndSaveGoalWithHistory(
            "Flexible Parent Goal", 
            GoalStatusEnum.WAITING, 
            null, 
            null, 
            null
        );

        Goal childGoal = createAndSaveGoalWithHistory(
            "Child Goal Ready", 
            GoalStatusEnum.WAITING, 
            LocalDate.now(), 
            LocalDate.now().plusDays(5), 
            parentWithNullDates
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalActivationJob.runGoalActivation();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedParent = goalRepository.findById(parentWithNullDates.getId()).orElseThrow();
        Goal updatedChild = goalRepository.findById(childGoal.getId()).orElseThrow();

        assertThat(updatedChild.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedParent.getStartDate()).isEqualTo(childGoal.getStartDate());

        List<GoalStatusHistory> parentHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(parentWithNullDates.getId(), testUser.getId());
        assertThat(parentHistory.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.UPWARD_CASCADE);
    }

    /**
     * Tests that activating an intermediate node within a three-level hierarchy
     * correctly propagates progress both upwards to the grandparent and downwards to the leaf node.
     */
    @Test
    @DisplayName("Should activate intermediate node and propagate both upwards and downwards")
    void runGoalActivation_IntermediateNode_PropagatesUpAndDown() {
        // Arrange
        Goal grandparent = createAndSaveGoalWithHistory(
            "Grandparent Goal", 
            GoalStatusEnum.WAITING, 
            null, 
            null, 
            null
        );

        Goal middleNode = createAndSaveGoalWithHistory(
            "Middle Goal", 
            GoalStatusEnum.WAITING, 
            LocalDate.now(), 
            LocalDate.now().plusDays(20), 
            grandparent
        );

        Goal leaf = createAndSaveGoalWithHistory(
            "Leaf Goal", 
            GoalStatusEnum.WAITING, 
            LocalDate.now().plusDays(5), 
            LocalDate.now().plusDays(10), 
            middleNode
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalActivationJob.runGoalActivation();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedGrandparent = goalRepository.findById(grandparent.getId()).orElseThrow();
        Goal updatedMiddle = goalRepository.findById(middleNode.getId()).orElseThrow();
        Goal updatedLeaf = goalRepository.findById(leaf.getId()).orElseThrow();

        assertThat(updatedMiddle.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedGrandparent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedLeaf.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);

        List<GoalStatusHistory> middleHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(middleNode.getId(), testUser.getId());
        assertThat(middleHistory.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.AUTO_ACTIVATION);

        List<GoalStatusHistory> grandparentHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(grandparent.getId(), testUser.getId());
        assertThat(grandparentHistory.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.UPWARD_CASCADE);

        List<GoalStatusHistory> leafHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(leaf.getId(), testUser.getId());
        assertThat(leafHistory.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.DOWNWARD_CASCADE);
    }

    /**
     * Tests the no-intervention scenario ensuring that goals with future start dates
     * remain untouched and stay in WAITING status.
     */
    @Test
    @DisplayName("Should NOT activate goals with future start dates (No-Intervention scenario)")
    void runGoalActivation_FutureGoal_RemainsWaiting() {
        // Arrange
        Goal futureGoal = createAndSaveGoalWithHistory(
            "Future Goal", 
            GoalStatusEnum.WAITING, 
            LocalDate.now().plusDays(5), 
            LocalDate.now().plusDays(30), 
            null
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalActivationJob.runGoalActivation();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedGoal = goalRepository.findById(futureGoal.getId()).orElseThrow();
        assertThat(updatedGoal.getStatus()).isEqualTo(GoalStatusEnum.WAITING);

        List<GoalStatusHistory> history = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(futureGoal.getId(), testUser.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);
    }

    /**
     * Helper method to persist a Goal along with its initial CREATION history record.
     *
     * @param title     the title of the goal
     * @param status    the initial status of the goal
     * @param startDate the planned start date
     * @param endDate   the planned end date
     * @param parent    the parent goal reference, if any
     * @return the successfully saved goal entity
     */
    private Goal createAndSaveGoalWithHistory(String title, GoalStatusEnum status, LocalDate startDate, LocalDate endDate, Goal parent) {
        Goal goal = new Goal();
        goal.setUserId(testUser.getId());
        goal.setTitle(title);
        goal.setStatus(status);
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setParent(parent);

        Goal savedGoal = goalRepository.save(goal);

        GoalStatusHistory creationHistory = new GoalStatusHistory(
            savedGoal,
            status,
            StatusTransitionCauseEnum.CREATION,
            null,
            OffsetDateTime.now()
        );
        goalStatusHistoryRepository.save(creationHistory);

        return savedGoal;
    }
}