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

import com.goal_tracker.AbstractIntegrationTest;
import com.goal_tracker.goals.entity.Goal;
import com.goal_tracker.goals.entity.GoalStatusEnum;
import com.goal_tracker.goals.entity.GoalStatusHistory;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.goals.entity.StatusTransitionCauseEnum;
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;
import com.goal_tracker.users.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Integration test suite for {@link GoalExpirationJob}, verifying automated
 * goal expiration logic, cascading terminal states, and scheduling boundary constraints.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoalExpirationJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GoalExpirationJob goalExpirationJob;

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
     * Tests that a single root goal in IN_PROGRESS status with a past end date
     * is successfully marked as FAILED with an AUTO_FAILURE history record.
     */
    @Test
    @DisplayName("Should fail single root goal when end date has passed")
    void runGoalExpirationSingleRootGoalSuccess() {
        // Arrange
        Goal rootGoal = createAndSaveGoalWithHistory(
            "Expired Root Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(10), 
            LocalDate.now().minusDays(1), 
            null
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalExpirationJob.runGoalExpiration();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedGoal = goalRepository.findById(rootGoal.getId()).orElseThrow();
        assertThat(updatedGoal.getStatus()).isEqualTo(GoalStatusEnum.FAILED);

        List<GoalStatusHistory> history = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(rootGoal.getId(), testUser.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getCause()).isEqualTo(StatusTransitionCauseEnum.CREATION);
        assertThat(history.get(1).getCause()).isEqualTo(StatusTransitionCauseEnum.AUTO_FAILURE);
        assertThat(history.get(1).getStatus()).isEqualTo(GoalStatusEnum.FAILED);
    }

    /**
     * Tests that failing a parent goal cascades DISCARDED to active/waiting children,
     * but skips children that are already in a terminal state (e.g., COMPLETED).
     */
    @Test
    @DisplayName("Should fail parent goal and cascade DISCARDED to children, skipping terminal ones")
    void runGoalExpirationParentGoalCascadesDiscardedSkippingTerminal() {
        // Arrange
        Goal parent = createAndSaveGoalWithHistory(
            "Expired Parent Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(10), 
            LocalDate.now().minusDays(1), 
            null
        );

        Goal activeChild = createAndSaveGoalWithHistory(
            "Active Child Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(5), 
            parent
        );

        Goal completedChild = createAndSaveGoalWithHistory(
            "Completed Child Goal", 
            GoalStatusEnum.COMPLETED, 
            LocalDate.now().minusDays(8), 
            LocalDate.now().minusDays(2), 
            parent
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalExpirationJob.runGoalExpiration();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        Goal updatedActiveChild = goalRepository.findById(activeChild.getId()).orElseThrow();
        Goal updatedCompletedChild = goalRepository.findById(completedChild.getId()).orElseThrow();

        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.FAILED);
        assertThat(updatedActiveChild.getStatus()).isEqualTo(GoalStatusEnum.DISCARDED);
        assertThat(updatedCompletedChild.getStatus()).isEqualTo(GoalStatusEnum.COMPLETED);

        List<GoalStatusHistory> activeChildHistory = goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(activeChild.getId(), testUser.getId());
        assertThat(activeChildHistory.get(activeChildHistory.size() - 1).getCause()).isEqualTo(StatusTransitionCauseEnum.DOWNWARD_CASCADE);
    }

    /**
     * Tests that failing an intermediate node marks it as FAILED, cascades DISCARDED downwards,
     * but leaves its parent unaffected.
     */
    @Test
    @DisplayName("Should fail intermediate node, cascade downwards, and leave parent unaffected")
    void runGoalExpirationIntermediateNodeFailsAndCascadesDownwardsOnly() {
        // Arrange
        Goal parent = createAndSaveGoalWithHistory(
            "Parent Goal Safe", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(20), 
            LocalDate.now().plusDays(10), 
            null
        );

        Goal intermediateNode = createAndSaveGoalWithHistory(
            "Expired Intermediate Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(10), 
            LocalDate.now().minusDays(1), 
            parent
        );

        Goal leaf = createAndSaveGoalWithHistory(
            "Leaf Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(2), 
            intermediateNode
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalExpirationJob.runGoalExpiration();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        Goal updatedIntermediate = goalRepository.findById(intermediateNode.getId()).orElseThrow();
        Goal updatedLeaf = goalRepository.findById(leaf.getId()).orElseThrow();

        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedIntermediate.getStatus()).isEqualTo(GoalStatusEnum.FAILED);
        assertThat(updatedLeaf.getStatus()).isEqualTo(GoalStatusEnum.DISCARDED);
    }

    /**
     * Tests bottom-up processing when a parent and its child expire on the same day:
     * the parent fails first and cascades downward closure.
     */
    @Test
    @DisplayName("Should process bottom-up when parent and children expire simultaneously, failing parent first")
    void runGoalExpirationSimultaneousExpirationBottomUp() {
        // Arrange
        Goal parent = createAndSaveGoalWithHistory(
            "Parent Expiring Today", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(10), 
            LocalDate.now().minusDays(1), 
            null
        );

        Goal child = createAndSaveGoalWithHistory(
            "Child Expiring Today", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(5), 
            LocalDate.now().minusDays(1), 
            parent
        );



        entityManager.flush();
        entityManager.clear();

        // Act
        goalExpirationJob.runGoalExpiration();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedParent = goalRepository.findById(parent.getId()).orElseThrow();
        Goal updatedChild = goalRepository.findById(child.getId()).orElseThrow();


        assertThat(updatedParent.getStatus()).isEqualTo(GoalStatusEnum.FAILED);
        assertThat(updatedChild.getStatus()).isEqualTo( GoalStatusEnum.FAILED);
    }

    /**
     * Tests a complex tree with mixed branches: some goals fail by expiration at different levels,
     * while others are discarded via downward cascades.
     */
    @Test
    @DisplayName("Should handle complex tree with mixed parallel failures and cascaded discards")
    void runGoalExpirationComplexTreeMixedFailuresAndCascades() {
        // Arrange
        Goal root = createAndSaveGoalWithHistory(
            "Root Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(30), 
            LocalDate.now().plusDays(10), 
            null
        );

        Goal branchA = createAndSaveGoalWithHistory(
            "Branch A (Expired)", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(15), 
            LocalDate.now().minusDays(1), 
            root
        );

        Goal leafA = createAndSaveGoalWithHistory(
            "Leaf A (Cascaded)", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(5), 
            branchA
        );

        Goal branchB = createAndSaveGoalWithHistory(
            "Branch B (Active)", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(15), 
            root
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalExpirationJob.runGoalExpiration();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedRoot = goalRepository.findById(root.getId()).orElseThrow();
        Goal updatedBranchA = goalRepository.findById(branchA.getId()).orElseThrow();
        Goal updatedLeafA = goalRepository.findById(leafA.getId()).orElseThrow();
        Goal updatedBranchB = goalRepository.findById(branchB.getId()).orElseThrow();

        assertThat(updatedRoot.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedBranchA.getStatus()).isEqualTo(GoalStatusEnum.FAILED);
        assertThat(updatedLeafA.getStatus()).isEqualTo(GoalStatusEnum.DISCARDED);
        assertThat(updatedBranchB.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
    }

    /**
     * Tests the no-intervention scenario ensuring that active goals with future end dates
     * or paused goals remain untouched.
     */
    @Test
    @DisplayName("Should NOT touch goals with future end dates or paused status (No-Intervention scenario)")
    void runGoalExpirationNoInterventionFutureOrPausedGoals() {
        // Arrange
        Goal futureGoal = createAndSaveGoalWithHistory(
            "Future Goal", 
            GoalStatusEnum.IN_PROGRESS, 
            LocalDate.now().minusDays(5), 
            LocalDate.now().plusDays(10), 
            null
        );

        Goal pausedGoal = createAndSaveGoalWithHistory(
            "Paused Expired Goal", 
            GoalStatusEnum.PAUSED, 
            LocalDate.now().minusDays(20), 
            LocalDate.now().minusDays(2), 
            null
        );

        entityManager.flush();
        entityManager.clear();

        // Act
        goalExpirationJob.runGoalExpiration();

        entityManager.flush();
        entityManager.clear();

        // Assert
        Goal updatedFuture = goalRepository.findById(futureGoal.getId()).orElseThrow();
        Goal updatedPaused = goalRepository.findById(pausedGoal.getId()).orElseThrow();

        assertThat(updatedFuture.getStatus()).isEqualTo(GoalStatusEnum.IN_PROGRESS);
        assertThat(updatedPaused.getStatus()).isEqualTo(GoalStatusEnum.PAUSED);
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