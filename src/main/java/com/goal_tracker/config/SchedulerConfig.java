package com.goal_tracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration class for the application task scheduler,
 * providing a dedicated thread pool for background cron jobs.
 */
@Configuration
public class SchedulerConfig {

    /**
     * Creates and configures a ThreadPoolTaskScheduler with a pool size of 3
     * to independently handle background tasks (Goal Activation, Goal Failure, and Guest Deletion).
     *
     * @return configured ThreadPoolTaskScheduler instance
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        
        threadPoolTaskScheduler.setPoolSize(4);
        
        threadPoolTaskScheduler.setThreadNamePrefix("goal-tracker-cron-pool-");
        
        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskScheduler.setAwaitTerminationSeconds(30);
        
        threadPoolTaskScheduler.initialize();
        return threadPoolTaskScheduler;
    }
}