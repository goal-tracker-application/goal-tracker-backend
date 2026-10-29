package com.goal_tracker.auth.job;

import com.goal_tracker.auth.service.AuthService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job responsible for cleaning up expired temporary guest user accounts.
 */
@Component
public class GuestCleanupJob {

    private final AuthService authService;

    public GuestCleanupJob(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Executes daily at midnight (00:00:00) to delete expired guest accounts and their associated data.
     * Expression format: second, minute, hour, day of month, month, day(s) of week.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runGuestCleanup() {
        authService.deleteExpiredGuests();
    }
}