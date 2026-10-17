package com.goal_tracker.goals.validation;

import com.goal_tracker.goals.dto.GoalCreateRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/**
 * Validator implementation for the {@link ValidDateRange} constraint applied to {@link GoalCreateRequestDto}.
 * <p>
 * Enforces business domain rules and database constraints for date boundaries:
 * <ul>
 *   <li>The planned start date cannot be set in the past.</li>
 *   <li>A planned end date requires an accompanying start date (mirrors the DB {@code CHECK} constraint).</li>
 *   <li>The end date must be equal to or greater than the start date.</li>
 * </ul>
 * </p>
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, GoalCreateRequestDto> {

    /**
     * Evaluates whether the date range provided in the {@link GoalCreateRequestDto} complies
     * with domain validation constraints.
     *
     * @param dto     The goal creation DTO being validated.
     * @param context The context in which the constraint is evaluated.
     * @return {@code true} if the date criteria are satisfied or if the DTO is null; {@code false} otherwise.
     */
    @Override
    public boolean isValid(GoalCreateRequestDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        LocalDate today = LocalDate.now();

        if (dto.getStartDate() != null && dto.getStartDate().isBefore(today)) {
            return false;
        }

        if (dto.getEndDate() != null && dto.getStartDate() == null) {
            return false;
        }

        if (dto.getStartDate() != null && dto.getEndDate() != null) {
            if (dto.getEndDate().isBefore(dto.getStartDate())) {
                return false;
            }
        }

        return true;
    }
}