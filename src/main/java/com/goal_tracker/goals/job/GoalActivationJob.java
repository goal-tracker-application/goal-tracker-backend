package com.goal_tracker.goals.job;

import com.goal_tracker.goals.service.GoalService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job responsible for triggering automatic goal activations based on start dates.
 */
@Component
public class GoalActivationJob {

    private final GoalService goalService;

    public GoalActivationJob(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * Executes daily at midnight (00:00:00) to activate scheduled goals.
     * Expression format: second, minute, hour, day of month, month, day(s) of week.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runGoalActivation() {
        goalService.activateScheduledGoals();
    }
}