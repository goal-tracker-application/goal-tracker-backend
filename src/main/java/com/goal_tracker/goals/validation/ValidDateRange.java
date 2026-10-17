package com.goal_tracker.goals.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to validate cross-field date logic on goal request DTOs.
 * Ensures start_date is not in the past, end_date requires start_date, and end_date >= start_date.
 */
@Documented
@Constraint(validatedBy = DateRangeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {

    String message() default "Invalid date range: startDate cannot be in the past, endDate requires startDate, and endDate must be equal to or after startDate.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}