package com.goal_tracker.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator class for the {@link ValidPassword} annotation.
 * <p>
 * This class implements the validation logic to ensure that a password meets the specified strength requirements.
 * </p>
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    /**
     * Minimum length for a valid password.
     */
    private static final int MIN_LENGTH = 10;

    /**
     * Maximum length for a valid password.
     */
    private static final int MAX_LENGTH = 72;

    /**
     * Regular expression pattern to check for at least one uppercase letter.
     */
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");

    /**
     * Regular expression pattern to check for at least one number.
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*[0-9].*");

    /**
     * Regular expression pattern to check for at least one special character.
     */
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(".*[^a-zA-Z0-9].*");

    /**
     * Validates the given password against the defined strength requirements.
     *
     * @param password The password to validate.
     * @param context  Context in which the constraint is evaluated.
     * @return {@code true} if the password meets all strength requirements; {@code false} otherwise.
     */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        if (password.length() < MIN_LENGTH) {
            return false;
        }

        if (password.length() > MAX_LENGTH) {
            return false;
        }

        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            return false;
        }

        if (!NUMBER_PATTERN.matcher(password).matches()) {
            return false;
        }

        return SYMBOL_PATTERN.matcher(password).matches();
    }
}