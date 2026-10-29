package com.goal_tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GoalTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoalTrackerApplication.class, args);
	}

}
