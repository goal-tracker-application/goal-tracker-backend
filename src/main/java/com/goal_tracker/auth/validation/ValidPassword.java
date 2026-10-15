package com.goal_tracker.auth.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for validating password strength.
 * <p>
 * This annotation can be applied to fields or parameters to enforce password strength requirements.
 * The validation logic is implemented in the {@link PasswordValidator} class.
 * </p>
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    /**
     * Default error message when the password does not meet the strength requirements.
     *
     * @return The interpolated error message.
     */
    String message() default "Password must contain at least 10 characters, no more than 72 characters, one uppercase letter, one number and one special character.";

    /**
     * Specifies the groups to which this constraint belongs.
     *
     * @return An array of group classes.
     */
    Class<?>[] groups() default {};

    /**
     * Payload that can be used by validation framework clients to assign
     * custom payload objects to a constraint.
     *
     * @return An array of payload classes.
     */
    Class<? extends Payload>[] payload() default {};
}