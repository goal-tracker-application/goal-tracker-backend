package com.goal_tracker.auth.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
import com.goal_tracker.goals.entity.StatusTransitionCauseEnum;
import com.goal_tracker.goals.repository.GoalRepository;
import com.goal_tracker.goals.repository.GoalStatusHistoryRepository;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Integration test suite for {@link GuestCleanupJob}, verifying automated
 * expiration and deletion logic for guest users, along with cascading goal and history cleanup.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuestCleanupJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GuestCleanupJob guestCleanupJob;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private GoalStatusHistoryRepository goalStatusHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Tests that expired guest users, along with their associated goals and status histories,
     * are successfully deleted by the cleanup job.
     */
    @Test
    @DisplayName("Should successfully delete expired guest users with their goals and histories")
    void runGuestCleanupDeletesExpiredGuestsSuccessfully() {
        User expiredGuest1 = new User();
        expiredGuest1.setEmail("guest_expired_1@goaltracker.local");
        expiredGuest1.setPasswordHash("dummyhash");
        expiredGuest1.setIsGuest(true);
        expiredGuest1.setExpiresAt(OffsetDateTime.now().minusHours(2));
        expiredGuest1 = userRepository.saveAndFlush(expiredGuest1);

        Goal goal1 = createAndSaveGoalWithHistory(
            expiredGuest1.getId(),
            "Expired Guest Goal 1",
            GoalStatusEnum.WAITING,
            LocalDate.now(),
            LocalDate.now().plusDays(5)
        );

        User expiredGuest2 = new User();
        expiredGuest2.setEmail("guest_expired_2@goaltracker.local");
        expiredGuest2.setPasswordHash("dummyhash");
        expiredGuest2.setIsGuest(true);
        expiredGuest2.setExpiresAt(OffsetDateTime.now().minusDays(1));
        expiredGuest2 = userRepository.saveAndFlush(expiredGuest2);

        Goal goal2 = createAndSaveGoalWithHistory(
            expiredGuest2.getId(),
            "Expired Guest Goal 2",
            GoalStatusEnum.WAITING,
            LocalDate.now(),
            LocalDate.now().plusDays(5)
        );

        entityManager.flush();
        entityManager.clear();

        guestCleanupJob.runGuestCleanup();

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(expiredGuest1.getId())).isEmpty();
        assertThat(userRepository.findById(expiredGuest2.getId())).isEmpty();
        assertThat(goalRepository.findById(goal1.getId())).isEmpty();
        assertThat(goalRepository.findById(goal2.getId())).isEmpty();
        assertThat(goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(goal1.getId(), expiredGuest1.getId())).isEmpty();
        assertThat(goalStatusHistoryRepository.findByGoalIdAndUserIdOrdered(goal2.getId(), expiredGuest2.getId())).isEmpty();
    }

    /**
     * Tests that active or non-expired guest users remain untouched by the cleanup job.
     */
    @Test
    @DisplayName("Should NOT delete guest users whose expiration time has not yet arrived")
    void runGuestCleanupIgnoresNonExpiredGuest() {
        User activeGuest = new User();
        activeGuest.setEmail("guest_active@goaltracker.local");
        activeGuest.setPasswordHash("dummyhash");
        activeGuest.setIsGuest(true);
        activeGuest.setExpiresAt(OffsetDateTime.now().plusHours(5));
        activeGuest = userRepository.saveAndFlush(activeGuest);

        Goal activeGoal = createAndSaveGoalWithHistory(
            activeGuest.getId(),
            "Active Guest Goal",
            GoalStatusEnum.WAITING,
            LocalDate.now(),
            LocalDate.now().plusDays(5)
        );

        entityManager.flush();
        entityManager.clear();

        guestCleanupJob.runGuestCleanup();

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(activeGuest.getId())).isPresent();
        assertThat(goalRepository.findById(activeGoal.getId())).isPresent();
    }

    /**
     * Tests the scenario where no expired guest users exist, ensuring the job completes successfully
     * without errors or side effects.
     */
    @Test
    @DisplayName("Should execute successfully without errors when there are no expired guest users")
    void runGuestCleanupDoesNothingWhenNoExpiredGuestsExist() {
        User regularUser = new User();
        regularUser.setEmail("regular.user@goaltracker.com");
        regularUser.setPasswordHash("dummyhash");
        regularUser.setIsGuest(false);
        regularUser.setExpiresAt(null);
        userRepository.saveAndFlush(regularUser);

        entityManager.flush();
        entityManager.clear();

        guestCleanupJob.runGuestCleanup();

        assertThat(userRepository.findById(regularUser.getId())).isPresent();
    }

    /**
     * Helper method to persist a Goal along with its initial CREATION history record for a specific user.
     *
     * @param userId    the identifier of the user owning the goal
     * @param title     the title of the goal
     * @param status    the initial status of the goal
     * @param startDate the planned start date
     * @param endDate   the planned end date
     * @return the successfully saved goal entity
     */
    private Goal createAndSaveGoalWithHistory(Long userId, String title, GoalStatusEnum status, LocalDate startDate, LocalDate endDate) {
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setTitle(title);
        goal.setStatus(status);
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setParent(null);

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