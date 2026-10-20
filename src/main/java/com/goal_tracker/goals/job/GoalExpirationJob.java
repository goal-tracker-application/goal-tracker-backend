package com.goal_tracker.goals.job;

import com.goal_tracker.goals.service.GoalService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job responsible for evaluating expired IN_PROGRESS goals
 * and marking them as FAILED automatically.
 */
@Component
public class GoalExpirationJob {

    private final GoalService goalService;

    public GoalExpirationJob(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * Executes daily at midnight to process expired goals.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runGoalExpiration() {
        goalService.expireScheduledGoals();
    }
}