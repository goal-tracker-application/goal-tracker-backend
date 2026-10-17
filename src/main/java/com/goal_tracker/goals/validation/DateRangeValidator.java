package com.goal_tracker.goals.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/**
 * Validator implementation for the {@link ValidDateRange} constraint applied to any {@link DateRangeAware} object.
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRangeAware> {

    /**
     * Evaluates whether the date range provided in the {@link DateRangeAware} object complies
     * with domain validation constraints.
     * <p>
     * Validation checks include:
     * <ul>
     *   <li>Ensuring the start date is not set in the past relative to today.</li>
     *   <li>Ensuring an end date cannot exist without a corresponding start date.</li>
     *   <li>Ensuring the end date is equal to or after the start date.</li>
     * </ul>
     * </p>
     *
     * @param dto     The date range aware object being validated.
     * @param context The context in which the constraint is evaluated.
     * @return {@code true} if the date criteria are satisfied or if the object is null; {@code false} otherwise.
     */
    @Override
    public boolean isValid(DateRangeAware dto, ConstraintValidatorContext context) {
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